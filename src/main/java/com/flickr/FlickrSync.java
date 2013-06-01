package com.flickr;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.ParserConfigurationException;

import org.scribe.model.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.flickr.oauth.OAuthHelper;
import com.flickr.utils.FlickrApi;

public class FlickrSync {
  File accessTokenFile;
  private static final Logger LOG = LoggerFactory.getLogger(FlickrSync.class);
  private static final String API_KEY = "392695576b5b4163d9db90e2dbe78f48";
  private static final String API_SECRET = "97eb70a795d41310";
  private static final String fileExtensions = ".jpg,.jpeg,.png";
  private Set<String> ignoredFoldersSet = new TreeSet<String>();
  private static final FileFilter filter = new FileFilter() {
    public boolean accept(File pathname) {
      if (pathname.isFile()) {
        String filename = pathname.getName().toLowerCase();
        String extensions[] = fileExtensions.split(",");
        boolean result = false;
        for (String ext : extensions) {
          result = result || filename.endsWith(ext);
        }
        return result;
      }
      return false;
    }
  };

  public FlickrSync(String foldersToBeIgnored) throws IOException {
    accessTokenFile = new File(System.getProperty("java.io.tmpdir"),
        "flickr.tok");
    if (foldersToBeIgnored != null) {
      String[] ignored = foldersToBeIgnored.split(",");
      for (String ignoredFoler : ignored) {
        ignoredFoldersSet.add(ignoredFoler);
      }
    }
  }

  private Token readAccessToken() throws IOException {
    if (!accessTokenFile.exists())
      return null;
    BufferedReader reader = new BufferedReader(new FileReader(accessTokenFile));
    Token token = new Token(reader.readLine(), reader.readLine());
    reader.close();
    return token;
  }

  private void writeAccessToken(Token token) throws IOException {
    if (!accessTokenFile.exists())
      accessTokenFile.createNewFile();
    BufferedWriter writer = new BufferedWriter(new FileWriter(accessTokenFile));
    writer.write(token.getToken());
    writer.newLine();
    writer.write(token.getSecret());
    writer.close();

  }

  private boolean isValidPhotoFile(File photo) {
    return filter.accept(photo);
  }

  private void getAllDirs(File parent, Map<String, File> dirs,
      Map<String, List<File>> folderToPhotos) {
    if (parent.isDirectory()) {
      if (!ignoredFoldersSet.contains(parent.getName())) {
        dirs.put(parent.getName(), parent);
        for (File f : parent.listFiles()) {
          if (f.isDirectory()) {
            getAllDirs(f, dirs, folderToPhotos);
          } else {
            if (isValidPhotoFile(f)) {
              String folderName = parent.getName();
              if (folderToPhotos.containsKey(folderName)) {
                folderToPhotos.get(folderName).add(f);
              } else {
                List<File> tmp = new ArrayList<File>();
                tmp.add(f);
                folderToPhotos.put(folderName, tmp);
              }
            }
          }
        }
      }
    }
  }

  private File[] getAllPhotos(File dir) {
    return dir.listFiles(filter);
  }

  private boolean uploadFolders(FlickrApi flickr, Map<String, File> folders,
      Token accessToken) throws IOException, ParserConfigurationException,
      SAXException {
    for (Entry<String, File> entry : folders.entrySet()) {
      File[] photos = getAllPhotos(entry.getValue());
      Set<File> photoSet = new HashSet<File>();
      for (File f : photos)
        photoSet.add(f);
      if (photoSet.size() > 0)
        if (!flickr.uploadPhotosToSet(entry.getKey(), photoSet, accessToken, true)) {
          return false;
        }

    }
    return true;
  }

  public static void main(String[] args) throws IOException,
      URISyntaxException, ParserConfigurationException, SAXException {
    if (args.length < 1) {
      System.out
          .println("Usage FlickSync [-deleteFromSource] <path to parent folder>");
      System.exit(0);
    }
    boolean deleteFromSource = false;
    String parentFolder = null;
    String foldersToBeIgnored = null;

    for (int i = 0; i < args.length;) {
      if (args[i].equalsIgnoreCase("-deleteFromSource")) {
        deleteFromSource = true;
        i++;

      } else if (args[i].equalsIgnoreCase("-foldersToBeIgnored")) {
        foldersToBeIgnored = args[i++];
        i++;
      } else
        parentFolder = args[i++];
    }

    FlickrSync sync = new FlickrSync(foldersToBeIgnored);
    OAuthHelper oAuth = new OAuthHelper(API_KEY, API_SECRET);
    FlickrApi flickr = new FlickrApi(oAuth);
    Token accessToken = sync.readAccessToken();
    if (accessToken == null) {
      Token requestToken = oAuth.getRequestToken();
      String authUrl = oAuth.getAuthUrl(requestToken);
      System.out.println("Enter the verifier code in console");
      if (!Desktop.isDesktopSupported()) {
        System.out.println("Please open the URL in browser" + authUrl);
      } else {
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
          System.out.println("Please open the URL in browser :" + authUrl);
        } else {
          desktop.browse(new URI(authUrl));
        }
      }
      String verifier = new BufferedReader(new InputStreamReader(System.in))
          .readLine();

      accessToken = oAuth.getAccessToken(verifier, requestToken);
      sync.writeAccessToken(accessToken);

    }
    Map<String, File> folders = new HashMap<String, File>();
    Map<String, PhotoSet> photosets = flickr.getAllSetsOfUser(accessToken);
    if (photosets == null) {
      LOG.error("Error while fetching sets of user;quitting");
      System.exit(-1);
    }
    Map<String, List<File>> folderToPhotos = new HashMap<String, List<File>>();
    sync.getAllDirs(new File(parentFolder), folders, folderToPhotos);
    Map<String, File> foldersNotUploaded = new HashMap<String, File>();
    for (Entry<String, File> folder : folders.entrySet()) {
      if (!photosets.containsKey(folder.getKey())) {
        foldersNotUploaded.put(folder.getKey(), folder.getValue());
      }

    }
    LOG.debug("All folders to be processed " + folders);
    LOG.debug("Folder Not Uploaded " + foldersNotUploaded);
    LOG.debug("Photos in each folder" + folderToPhotos);
    if (!sync.uploadFolders(flickr, foldersNotUploaded, accessToken)) {
      LOG.error("Cannot upload all new sets;Quitting");
      System.exit(-1);
    }
    for (Entry<String, List<File>> entry : folderToPhotos.entrySet()) {
      String setName = entry.getKey();
      if (photosets.containsKey(setName)) {
        PhotoSet photoSet = photosets.get(setName);
        List<File> photosInFolder = folderToPhotos.get(setName);
        Set<File> photosToBeUploaded = new HashSet<File>();
        Set<String> photosInSet = flickr.getAllPhotosInSet(photoSet.getId(),
            accessToken);
        for (File photo : photosInFolder) {
          if (!photosInSet.contains(photo.getName())) {
            photosToBeUploaded.add(photo);
          } else {
            if (deleteFromSource) {
              LOG.info("Deleting photo from source " + photo.getAbsolutePath());
              photo.delete();
            }
          }
        }
        if (photosToBeUploaded.size() > 0) {
          LOG.info("Photos to be uploaded in Set " + setName + " are "
              + photosToBeUploaded.size() + photosToBeUploaded);

          flickr.uploadPhotosToSet(photoSet.getId(), photosToBeUploaded,
              accessToken, false);
        }
      }
    }
    System.out.println("Thanks for using FlickrSync");
  }

}
