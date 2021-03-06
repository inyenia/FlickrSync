package com.flickr.utils;

import static com.flickr.FlickrConstants.API_KEY;
import static com.flickr.FlickrConstants.FLICKR_METHOD_ADD_PHOTO_TO_SET;
import static com.flickr.FlickrConstants.FLICKR_METHOD_CREATE_SET;
import static com.flickr.FlickrConstants.FLICKR_METHOD_GETPHOTOS;
import static com.flickr.FlickrConstants.FLICKR_METHOD_GET_SET_PHOTOS;
import static com.flickr.FlickrConstants.HIDDEN;
import static com.flickr.FlickrConstants.ID;
import static com.flickr.FlickrConstants.IS_FAMILY;
import static com.flickr.FlickrConstants.IS_FRIEND;
import static com.flickr.FlickrConstants.IS_PUBLIC;
import static com.flickr.FlickrConstants.PAGE;
import static com.flickr.FlickrConstants.PAGES;
import static com.flickr.FlickrConstants.PHOTO;
import static com.flickr.FlickrConstants.PHOTOS;
import static com.flickr.FlickrConstants.PHOTOSET;
import static com.flickr.FlickrConstants.PHOTOSET_ID;
import static com.flickr.FlickrConstants.PHOTO_ID;
import static com.flickr.FlickrConstants.PHOTO_UNEDRSCORE_ID;
import static com.flickr.FlickrConstants.PRIMARY_PHOTO_ID;
import static com.flickr.FlickrConstants.TITLE;
import static com.flickr.FlickrConstants.VIDEOS;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.apache.http.Header;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Request;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.flickr.PhotoSet;
import com.flickr.oauth.OAuthHelper;

public class FlickrApi {
  private static final Logger LOG = LoggerFactory.getLogger(FlickrApi.class);
  private OAuthHelper oAuth;
  private static int numOfThreads = 5;
  private static final String NUM_OF_THREADS = "flickrSync.numThreads";
  private static final MetricRegistry metrics = new MetricRegistry();
  public static final Timer setAdditionMetrics = metrics.timer(MetricRegistry
      .name(FlickrApi.class, "setAddition"));
  public static final Counter uploadFailure = metrics.counter(MetricRegistry
      .name(
      FlickrApi.class, "uploadFailure"));

  public FlickrApi(OAuthHelper oAuth) {
    this.oAuth = oAuth;
    String numThreads = System.getProperty(NUM_OF_THREADS);
    if (numThreads != null) {
      numOfThreads = Integer.parseInt(numThreads);
      ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
          .convertRatesTo(TimeUnit.MINUTES)
          .convertDurationsTo(TimeUnit.SECONDS).build();
      reporter.start(1, TimeUnit.MINUTES);
    }

  }

  private class CallBackHandler implements CallBack<Response> {

    private FlickrApi flickr;
    private Token accessToken;
    private String photoSetId;

    public CallBackHandler(String photoSetId, FlickrApi flickr,
        Token accessToken) {
      this.photoSetId = photoSetId;
      this.flickr = flickr;
      this.accessToken = accessToken;
    }

    public void call(Response response) {
      try {
        LOG.debug("Got a callback");
        String photoId = getPhotoIdFromResponse(response);
        if (photoId != null) {
          flickr.addPhotoToSet(photoSetId, photoId, accessToken);
        }

      } catch (Exception e) {
        LOG.error("Error in call back", e);
      }

    }

  }

  public static String getApiUrl(String method) {
    String url = "http://api.flickr.com/services/rest?method=" + method;
    return url;
  }

  private void addQueryParams(OAuthRequest request, Map<String, String> params) {
    for (Entry<String, String> entry : params.entrySet()) {
      request.addQuerystringParameter(entry.getKey(), entry.getValue());
    }
  }

  private void addBodyParams(OAuthRequest request, Map<String, String> params) {
    for (Entry<String, String> entry : params.entrySet()) {
      request.addBodyParameter(entry.getKey(), entry.getValue());
    }
  }

  public Map<String, PhotoSet> getAllSetsOfUser(Token accessToken) {
    Map<String, String> params = new HashMap<String, String>();
    params.put(API_KEY, oAuth.getApiKey());
    String methodName = FLICKR_METHOD_GET_SET_PHOTOS;
    OAuthRequest request = new OAuthRequest(Verb.GET, getApiUrl(methodName));
    addQueryParams(request, params);
    Response response = oAuth.sendRequest(request, accessToken);
    String responseBody = response.getBody();
    LOG.debug("Response of getAllSetsOfUser " + responseBody);
    Map<String, PhotoSet> photosets = new HashMap<String, PhotoSet>();
    try {
      Document doc = XMLUtil.getXMLDoc(responseBody);
      NodeList photosetsNodeList = doc.getDocumentElement()
          .getElementsByTagName("photosets");
      if (photosetsNodeList.getLength() == 0) {
        LOG.info("User doesn't have any set uploaded on flickr");
        return photosets;
      }
      Element element = (Element) (photosetsNodeList.item(0));
      NodeList sets = element.getElementsByTagName(PHOTOSET);
      for (int i = 0; i < sets.getLength(); i++) {
        Node photoset = sets.item(i);
        NamedNodeMap attributes = photoset.getAttributes();
        String id = attributes.getNamedItem(ID).getNodeValue();
        int numOfPhotos = Integer.parseInt(attributes.getNamedItem(PHOTOS)
            .getNodeValue());
        int numOfVidoes = Integer.parseInt(attributes.getNamedItem(VIDEOS)
            .getNodeValue());
        Element photoSetElement = (Element) photoset;
        String title = photoSetElement.getElementsByTagName(TITLE).item(0)
            .getTextContent();
        photosets.put(title, new PhotoSet(id, numOfPhotos, numOfVidoes, title));

      }
    } catch (Exception e) {
      // catching all exceptions while parsing;it signifies an unexpected
      // response + other i/o exceptions
      return null;
    }
    return photosets;

  }

  private String getPhotoIdFromResponse(Response response) {
    String responseBody = response.getBody();
    LOG.debug("Response from which photo id is to be parsed is " + responseBody);
    String value = null;
    try {
    Document doc = XMLUtil.getXMLDoc(responseBody);
    NodeList photoIds = doc.getDocumentElement().getElementsByTagName(PHOTO_ID);
    if (photoIds.getLength() == 0)
      return null;
    Element element = (Element) (photoIds.item(0));
      value = element.getTextContent();
      LOG.debug("Photo id parsed is " + value);

    } catch (Exception e) {
      LOG.error("Error while parsing photo id");
      uploadFailure.inc();
    }
    return value;

  }


  private Request getRequestForPhotoUpload(String photoName, File photo,
      Token accessToken) {
    String url = "http://api.flickr.com/services/upload/";
    OAuthRequest request = new OAuthRequest(Verb.POST, url);
    Map<String, String> postParams = new HashMap<String, String>();
    postParams.put(TITLE, photoName);
    postParams.put(IS_PUBLIC, "0");
    postParams.put(IS_FRIEND, "0");
    postParams.put(IS_FAMILY, "0");
    postParams.put(HIDDEN, "2");
    addBodyParams(request, postParams);
    oAuth.signRequest(request, accessToken);
    MultipartEntity reqEntity = new MultipartEntity();
    reqEntity.addPart(TITLE,
        new StringBody(photoName, ContentType.DEFAULT_TEXT));
    reqEntity.addPart(IS_PUBLIC, new StringBody("0", ContentType.DEFAULT_TEXT));
    reqEntity.addPart(IS_FRIEND, new StringBody("0", ContentType.DEFAULT_TEXT));
    reqEntity.addPart(IS_FAMILY, new StringBody("0", ContentType.DEFAULT_TEXT));
    reqEntity.addPart(HIDDEN, new StringBody("2", ContentType.DEFAULT_TEXT));
    reqEntity.addPart(PHOTO, new FileBody(photo,
        ContentType.APPLICATION_OCTET_STREAM, photoName));
    ByteArrayOutputStream bos = new ByteArrayOutputStream(
        (int) reqEntity.getContentLength());
    try {
      reqEntity.writeTo(bos);
    } catch (IOException e) {
      LOG.error("Error while preparing upload request", e);
      return null;
    }
    request.addPayload(bos.toByteArray());

    Header contentType = reqEntity.getContentType();
    request.addHeader(contentType.getName(), contentType.getValue());
    return request;

  }

  public String uploadPhoto(String photoName, File photo, Token accessToken) {
    Request request = getRequestForPhotoUpload(photoName, photo, accessToken);
    Response response;
    response = request.send();
    return getPhotoIdFromResponse(response);
  }
  public void uploadPhotos(Map<String, File> photos, Token accessToken,
      CallBack<Response> callback) {
    MultiThreadedRequestExecution executor = new MultiThreadedRequestExecution(
        numOfThreads, callback);
    for (Entry<String, File> photo : photos.entrySet()) {
      Request request = getRequestForPhotoUpload(photo.getKey(),
          photo.getValue(), accessToken);
      if (request != null)
      executor.addRequest(request);
    }
    executor.shutdown();
    executor.waitForCompletion();
  }

  public boolean addPhotosToSet(String photoSetId, List<String> photoIds,
      Token accessToken) {
    boolean success = true;
    for (String photoId : photoIds) {
      if (!addPhotoToSet(photoSetId, photoId, accessToken))
        success = false;
    }
    return success;
  }


  private boolean addPhotoToSet(String photoSetId, String photoId,
      Token accessToken) {
    LOG.debug("Adding photo " + photoId + " to set " + photoSetId);
    String url = getApiUrl(FLICKR_METHOD_ADD_PHOTO_TO_SET);
    Map<String, String> params = new HashMap<String, String>();
    params.put(API_KEY, oAuth.getApiKey());
    params.put(PHOTOSET_ID, photoSetId);
    params.put(PHOTO_UNEDRSCORE_ID, photoId);
    OAuthRequest request = new OAuthRequest(Verb.POST, url);
    addBodyParams(request, params);
    Response response;
    final Timer.Context context = setAdditionMetrics.time();
    try {
      response = oAuth.sendRequest(request, accessToken);
    } finally {
      context.stop();
    }
    String responseBody = response.getBody();
    LOG.debug("Response of adding photo to set is " + responseBody);
    try {
    Document doc = XMLUtil.getXMLDoc(responseBody);
    String status = doc.getFirstChild().getAttributes().getNamedItem("stat")
        .getNodeValue();
    if (status.equals("fail")) {
      Element el = ((Element) (doc.getElementsByTagName("err").item(0)));
      String code = el.getAttribute("code");
      // ignoring the case when already added photo was re tried
      if (!code.equals("3"))
        return false;
    }
    } catch (Exception e) {
      LOG.error("Exception in adding photo to set", e);
      return false;
    }

    LOG.info("Added photo " + photoId + " to set " + photoSetId);
    return true;
  }

  private String createSet(String name, String primayPhotoId, Token accessToken) {
    String url = getApiUrl(FLICKR_METHOD_CREATE_SET);
    Map<String, String> params = new HashMap<String, String>();
    params.put(API_KEY, oAuth.getApiKey());
    params.put(TITLE, name);
    params.put(PRIMARY_PHOTO_ID, primayPhotoId);
    OAuthRequest request = new OAuthRequest(Verb.POST, url);
    addBodyParams(request, params);
    Response response = oAuth.sendRequest(request, accessToken);
    String responseBody = response.getBody();
    LOG.debug("Response of creating set " + responseBody);
    Document doc;
    try {
      doc = XMLUtil.getXMLDoc(responseBody);
    } catch (Exception e) {
      LOG.error("Exception while creating set ", e);
      return null;
    }
    NodeList photosets = doc.getDocumentElement()
        .getElementsByTagName(PHOTOSET);
    if (photosets.getLength() == 0)
      return null;
    Element element = (Element) ((photosets).item(0));
    LOG.info("Created set " + name);
    return element.getAttribute("id");

  }

  private Element getPhotosInSetOfSinglePage(String setId, Token accessToken,
      int page) {
    String url = getApiUrl(FLICKR_METHOD_GETPHOTOS);
    Map<String, String> params = new HashMap<String, String>();
    params.put(API_KEY, oAuth.getApiKey());
    params.put(PHOTOSET_ID, setId);
    params.put(PAGE, page + "");
    OAuthRequest request = new OAuthRequest(Verb.GET, url);
    addQueryParams(request, params);
    Response response = oAuth.sendRequest(request, accessToken);
    String responseBody = response.getBody();
    LOG.debug("Response of getting all photos in a set " + responseBody);
    Document doc;
    try {
      doc = XMLUtil.getXMLDoc(responseBody);
    } catch (Exception e) {
      LOG.error("Error while getting photos of set " + setId, e);
      return null;
    }
    Element docElement = doc.getDocumentElement();
    return docElement;
  }

  public Set<String> getAllPhotosInSet(String setId, Token accessToken) {
    Set<String> photos = new TreeSet<String>();
    Element docElement = getPhotosInSetOfSinglePage(setId, accessToken, 1);
    NodeList photosets = docElement.getElementsByTagName(PHOTOSET);
    photos.addAll(getPhotoTitlesFromDocElement(docElement));
    int numOfPages = 1;
    int total = 0;
    if (photosets.getLength() > 0) {
      Node pages = photosets.item(0).getAttributes().getNamedItem(PAGES);
      if (pages != null) {
        numOfPages = Integer.parseInt(pages.getNodeValue());
      }
      Node totalNode = photosets.item(0).getAttributes().getNamedItem("total");
      if (totalNode != null) {
        total = Integer.parseInt(totalNode.getNodeValue());
      }
    }
    if (numOfPages > 1) {
      for (int i = 2; i <= numOfPages; i++) {
        docElement = getPhotosInSetOfSinglePage(setId, accessToken, i);
        photos.addAll(getPhotoTitlesFromDocElement(docElement));
      }
    }

    return photos;
  }

  private Set<String> getPhotoTitlesFromDocElement(Element docElement) {
    NodeList nodes = docElement.getElementsByTagName(PHOTO);
    Set<String> photos = new TreeSet<String>();
    LOG.debug("Total photos in the response " + nodes.getLength());
    for (int i = 0; i < nodes.getLength(); i++) {
      Element element = (Element) nodes.item(i);
      photos.add(element.getAttribute(TITLE));
    }
    return photos;
  }
  public boolean uploadPhotosToSet(String id, Set<File> filesToBeUploaded,
      Token accessToken, boolean createNewSet) {
    String setId = null;
    Map<String, File> photos = new HashMap<String, File>();

    File primePhoto = null;
    try {
      Iterator<File> fileIterator = filesToBeUploaded.iterator();
      if (createNewSet && fileIterator.hasNext()) {
        LOG.debug("Uploading the primary photo for the set ");
        primePhoto = fileIterator.next();
        Map<String, File> primaryPhoto = new HashMap<String, File>();
        primaryPhoto.put(primePhoto.getName(), primePhoto);
        String primaryPhotoId = uploadPhoto(primePhoto.getName(), primePhoto,
            accessToken);
        LOG.info("creating set with name " + id);
        setId = createSet(id, primaryPhotoId, accessToken);
        LOG.debug("Set created with name " + id + " and id " + setId);
      }
      CallBack<Response> callback;
      while (fileIterator.hasNext()) {
        File photo = fileIterator.next();
        photos.put(photo.getName(), photo);
      }
      if (!createNewSet)
        setId = id;
      if (setId != null) {
        LOG.debug("Uploading photos " + photos);
        callback = new CallBackHandler(setId, this, accessToken);
        uploadPhotos(photos, accessToken, callback);
        LOG.info("Photos uploaded and added to set with id " + id);
      } else {
        return false;
      }
    } catch (Exception e) {
      LOG.error("Error while addingPhotos to set ", e);
      return false;
    }
    return true;
  }

}
