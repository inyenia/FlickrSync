package com.flickr.utils;

import static com.flickr.FlickrConstants.FLICKR_METHOD_DELETE_PHOTO;
import static com.flickr.FlickrConstants.FLICKR_METHOD_GETPHOTOS;
import static com.flickr.FlickrConstants.PAGE;
import static com.flickr.FlickrConstants.PAGES;
import static com.flickr.FlickrConstants.PHOTO;
import static com.flickr.FlickrConstants.PHOTOSET;
import static com.flickr.FlickrConstants.PHOTOSET_ID;
import static com.flickr.FlickrConstants.PHOTO_UNEDRSCORE_ID;
import static com.flickr.FlickrConstants.TITLE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.flickr.oauth.OAuthHelper;

public class TestCode {
  private static final String API_KEY = "392695576b5b4163d9db90e2dbe78f48";
  private static final String API_SECRET = "97eb70a795d41310";
  private static OAuthHelper oAuth = new OAuthHelper(API_KEY, API_SECRET);
  
  public static String[] run() {

    String csvFile = "/Users/rohit.kochar/Desktop/duplicate dropbox.txt";
    BufferedReader br = null;
    String line = "";
    String cvsSplitBy = ",";
    String[] dupIds = null;
    try {

      br = new BufferedReader(new FileReader(csvFile));
      while ((line = br.readLine()) != null) {

        // use comma as separator
        dupIds = line.split(cvsSplitBy);
      }

    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (br != null) {
        try {
          br.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    System.out.println("duplicates ids" + Arrays.toString(dupIds));
    return dupIds;
  }

  private static void addBodyParams(OAuthRequest request,
      Map<String, String> params) {
    for (Entry<String, String> entry : params.entrySet()) {
      request.addBodyParameter(entry.getKey(), entry.getValue());
    }
  }

  public static void deletePhoto(String photoId, Token accessToken) {
    String url = getApiUrl(FLICKR_METHOD_DELETE_PHOTO);
    Map<String, String> params = new HashMap<String, String>();
    params.put(API_KEY, oAuth.getApiKey());
    params.put(PHOTO_UNEDRSCORE_ID, photoId);
    OAuthRequest request = new OAuthRequest(Verb.POST, url);
    addBodyParams(request, params);
    Response response;
    response = oAuth.sendRequest(request, accessToken);
    String responseBody = response.getBody();
    System.out.println("Response of deleting photo with id " + photoId + "is "
        + responseBody);

  }

  /**
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    Token accessToken = null;// AccessTokenUtil.readAccessToken();

    if (accessToken == null) {
      accessToken = AccessTokenUtil.getAccessToken(oAuth);
      AccessTokenUtil.writeAccessToken(accessToken);
    }
   
    List<String> dupIds = getAllDupPhotosInSet("72157633852969536", accessToken);
    System.out.println("Total dupIds " + dupIds.size() + dupIds);
    for (String id : dupIds)
      deletePhoto(id, accessToken);
  }

  private static void addQueryParams(OAuthRequest request,
      Map<String, String> params) {
    for (Entry<String, String> entry : params.entrySet()) {
      request.addQuerystringParameter(entry.getKey(), entry.getValue());
    }
  }

  public static String getApiUrl(String method) {
    String url = "http://api.flickr.com/services/rest?method=" + method;
    return url;
  }

  private static Element getPhotosInSetOfSinglePage(String setId,
      Token accessToken,
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
    System.out.println("Response of getting all photos in a set "
        + responseBody);
    Document doc;
    try {
      doc = XMLUtil.getXMLDoc(responseBody);
    } catch (Exception e) {
      System.err.println("Error while getting photos of set " + setId);
      return null;
    }
    Element docElement = doc.getDocumentElement();
    return docElement;
  }

  public static List<String> getAllDupPhotosInSet(String setId,
      Token accessToken) {
    List<String> photos = new ArrayList<String>();
    Element docElement = getPhotosInSetOfSinglePage(setId, accessToken, 1);
    NodeList photosets = docElement.getElementsByTagName(PHOTOSET);
    Map<String, String> allPhotos = new HashMap<String, String>();
    photos.addAll(getPhotoTitlesFromDocElement(docElement, allPhotos));
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
        photos.addAll(getPhotoTitlesFromDocElement(docElement, allPhotos));
      }
    }

    Set<String> titles = allPhotos.keySet();
    titles.removeAll(getAllPhotosInFolder());
    System.out.println("Photos not present in folder but in set"
        + titles.size() + allPhotos);
    return photos;
  }

  private static Set<String> getAllPhotosInFolder() {
    String file = "/Users/rohit.kochar/Dropbox/Camera Uploads";
    File dir = new File(file);

    Set<String> files = new TreeSet<String>();
    files.addAll(Arrays.asList(dir.list()));
    return files;

  }

  private static List<String> getPhotoTitlesFromDocElement(Element docElement,
      Map<String, String> photos) {
    NodeList nodes = docElement.getElementsByTagName(PHOTO);

    List<String> duplicates =new ArrayList<String>();
    System.out.println("Total photos in the response " + nodes.getLength());
    for (int i = 0; i < nodes.getLength(); i++) {
      Element element = (Element) nodes.item(i);
      String title=element.getAttribute(TITLE);
      String id=element.getAttribute("id");
      if (photos.containsKey(title)) {
        duplicates.add(id);
        System.out.println("Old id " + photos.get(title) + " new id " + id);
      }
      else
        photos.put(title, id);
    }
    return duplicates;
  }

 

}
