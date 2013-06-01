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
import static com.flickr.FlickrConstants.PHOTO;
import static com.flickr.FlickrConstants.PHOTOS;
import static com.flickr.FlickrConstants.PHOTOSET;
import static com.flickr.FlickrConstants.PHOTOSET_ID;
import static com.flickr.FlickrConstants.PHOTO_ID;
import static com.flickr.FlickrConstants.PHOTO_UNERSCORE_ID;
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

import javax.xml.parsers.ParserConfigurationException;

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
import org.xml.sax.SAXException;

import com.flickr.PhotoSet;
import com.flickr.oauth.OAuthHelper;

public class FlickrApi {
  private static final Logger LOG = LoggerFactory.getLogger(FlickrApi.class);
  private OAuthHelper oAuth;
  private static int numOfThreads = 10;
  private static final String NUM_OF_THREADS = "flickrSync.numThreads";

  public FlickrApi(OAuthHelper oAuth) {
    this.oAuth = oAuth;
    String numThreads = System.getProperty(NUM_OF_THREADS);
    if (numThreads != null) {
      numOfThreads = Integer.parseInt(numThreads);
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
        e.printStackTrace();
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

  public Map<String, PhotoSet> getAllSetsOfUser(Token accessToken)
      throws ParserConfigurationException, SAXException, IOException {
    Map<String, String> params = new HashMap<String, String>();
    params.put(API_KEY, oAuth.getApiKey());
    String methodName = FLICKR_METHOD_GET_SET_PHOTOS;
    OAuthRequest request = new OAuthRequest(Verb.GET, getApiUrl(methodName));
    addQueryParams(request, params);
    Response response = oAuth.sendRequest(request, accessToken);
    String responseBody = response.getBody();
    LOG.debug("Response of getAllSetsOfUser " + responseBody);
    Document doc = XMLUtil.getXMLDoc(responseBody);
    Map<String, PhotoSet> photosets = new HashMap<String, PhotoSet>();
    try {
      NodeList photosetsNodeList = doc.getDocumentElement()
          .getElementsByTagName("photosets");
      if (photosetsNodeList.getLength() == 0) {
        LOG.debug("User doesn't have any set uploaded on flickr");
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

  private String getPhotoIdFromResponse(Response response)
      throws ParserConfigurationException, SAXException, IOException {
    String responseBody = response.getBody();
    LOG.debug("Response from which photo id is to be parsed is " + responseBody);
    Document doc = XMLUtil.getXMLDoc(responseBody);
    NodeList photoIds = doc.getDocumentElement().getElementsByTagName(PHOTO_ID);
    if (photoIds.getLength() == 0)
      return null;
    Element element = (Element) (photoIds.item(0));
    LOG.debug("Photo id parsed is " + element.getTextContent());
    return element.getTextContent();

  }


  private Request getRequestForPhotoUpload(String photoName, File photo,
      Token accessToken) throws IOException {
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
    reqEntity.writeTo(bos);
    request.addPayload(bos.toByteArray());

    Header contentType = reqEntity.getContentType();
    request.addHeader(contentType.getName(), contentType.getValue());
    return request;

  }

  public String uploadPhoto(String photoName, File photo, Token accessToken)
      throws IOException, ParserConfigurationException, SAXException {
    Request request = getRequestForPhotoUpload(photoName, photo, accessToken);
    Response response = request.send();
    return getPhotoIdFromResponse(response);
  }
  public void uploadPhotos(Map<String, File> photos, Token accessToken,
      CallBack<Response> callback) throws IOException,
      ParserConfigurationException, SAXException, InterruptedException {
    MultiThreadedRequestExecution executor = new MultiThreadedRequestExecution(
        numOfThreads, callback);
    for (Entry<String, File> photo : photos.entrySet()) {
      Request request = getRequestForPhotoUpload(photo.getKey(),
          photo.getValue(), accessToken);
      executor.addRequest(request);
    }
    executor.shutdown();
    executor.waitForCompletion();
  }

  public boolean addPhotosToSet(String photoSetId, List<String> photoIds,
      Token accessToken) throws ParserConfigurationException, SAXException,
      IOException {
    boolean success = true;
    for (String photoId : photoIds) {
      if (!addPhotoToSet(photoSetId, photoId, accessToken))
        success = false;
    }
    return success;
  }

  private boolean addPhotoToSet(String photoSetId, String photoId,
      Token accessToken) throws ParserConfigurationException, SAXException,
      IOException {
    LOG.debug("Adding photo " + photoId + " to set " + photoSetId);
    String url = getApiUrl(FLICKR_METHOD_ADD_PHOTO_TO_SET);
    Map<String, String> params = new HashMap<String, String>();
    params.put(API_KEY, oAuth.getApiKey());
    params.put(PHOTOSET_ID, photoSetId);
    params.put(PHOTO_UNERSCORE_ID, photoId);
    OAuthRequest request = new OAuthRequest(Verb.POST, url);
    addBodyParams(request, params);
    Response response = oAuth.sendRequest(request, accessToken);
    String responseBody = response.getBody();
    LOG.debug("Response of adding photo to set is " + responseBody);
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
    LOG.debug("Added photo " + photoId + " to set " + photoSetId);
    return true;
  }

  private String createSet(String name, String primayPhotoId, Token accessToken)
      throws ParserConfigurationException, SAXException, IOException {
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
    Document doc = XMLUtil.getXMLDoc(responseBody);
    NodeList photosets = doc.getDocumentElement()
        .getElementsByTagName(PHOTOSET);
    if (photosets.getLength() == 0)
      return null;
    Element element = (Element) ((photosets).item(0));
    return element.getAttribute("id");

  }

  public Set<String> getAllPhotosInSet(String setId, Token accessToken)
      throws ParserConfigurationException, SAXException, IOException {
    String url = getApiUrl(FLICKR_METHOD_GETPHOTOS);
    Map<String, String> params = new HashMap<String, String>();
    params.put(API_KEY, oAuth.getApiKey());
    params.put(PHOTOSET_ID, setId);
    OAuthRequest request = new OAuthRequest(Verb.GET, url);
    addQueryParams(request, params);
    Response response = oAuth.sendRequest(request, accessToken);
    String responseBody = response.getBody();
    LOG.debug("Response of getting all photos in a set " + responseBody);
    Document doc = XMLUtil.getXMLDoc(responseBody);
    NodeList nodes = doc.getDocumentElement().getElementsByTagName(PHOTO);
    Set<String> photos = new TreeSet<String>();
    for (int i = 0; i < nodes.getLength(); i++) {
      Element element = (Element) nodes.item(i);
      photos.add(element.getAttribute(TITLE));
    }
    return photos;
  }

  public boolean uploadPhotosToSet(String id, Set<File> filesToBeUploaded,
      Token accessToken, boolean createNewSet) throws IOException,
      ParserConfigurationException, SAXException {
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
        LOG.info("Photos uploaded and added to set");
      }
    } catch (Exception e) {
      LOG.error("Error while addingPhotos to set ", e);
      return false;
    }
    return true;
  }

}
