package com.flickr;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.scribe.model.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Timer;
import com.flickr.oauth.OAuthHelper;
import com.flickr.utils.AccessTokenUtil;
import com.flickr.utils.FlickrApi;
import com.flickr.utils.MultiThreadedRequestExecution;

public class FlickrSync {

  private static final Logger LOG = LoggerFactory.getLogger(FlickrSync.class);
  private static final String API_KEY = "392695576b5b4163d9db90e2dbe78f48";
  private static final String API_SECRET = "97eb70a795d41310";
  private static final String fileExtensions = ".jpg,.jpeg,.png";
  private Set<String> ignoredFoldersSet = new TreeSet<String>();
  private Map<String,String> skippedFolders= new HashMap<String, String>();
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

  public FlickrSync(String foldersToBeIgnored) {
    if (foldersToBeIgnored != null) {
      String[] ignored = foldersToBeIgnored.split(",");
      for (String ignoredFoler : ignored) {
        ignoredFoldersSet.add(ignoredFoler);
      }
    }
  }





  private boolean isValidPhotoFile(File photo) {
    return filter.accept(photo);
  }

  private void getAllDirs(File parent, Map<String, File> dirs,
      Map<String, List<File>> folderToPhotos) {
    if (parent.isDirectory()) {
      if (!ignoredFoldersSet.contains(parent.getName())) {
	  if(dirs.containsKey(parent.getName())){
	      skippedFolders.put(parent.getAbsolutePath(), dirs.get(parent.getName()).getAbsolutePath());
	      return;
	  }
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
      Token accessToken) {
    for (Entry<String, File> entry : folders.entrySet()) {
      File[] photos = getAllPhotos(entry.getValue());
      Set<File> photoSet = new HashSet<File>();
      for (File f : photos)
        photoSet.add(f);
      if (photoSet.size() > 0)
        if (!flickr.uploadPhotosToSet(entry.getKey(), photoSet, accessToken,
            true)) {
          return false;
        }

    }
    return true;
  }

  private  void printFinalMetrics() {
      
    Counter uploadFailure = FlickrApi.uploadFailure;
    System.out.println("Total upload failed " + uploadFailure.getCount());
    Timer photoUpload = MultiThreadedRequestExecution.requestMetrics;
    System.out.println("Total photos uploaded " + photoUpload.getCount());
    Timer setAddition = FlickrApi.setAdditionMetrics;
    System.out.println("Total photos added to set " + setAddition.getCount());
  }
  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      System.out
          .println("Usage FlickSync [-deleteFromSource] [-foldersToBeIgnored <comma separated list of folder>]<path to parent folder>");
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
        foldersToBeIgnored = args[++i];
        i++;
      } else
        parentFolder = args[i++];
    }

    FlickrSync sync = new FlickrSync(foldersToBeIgnored);
    OAuthHelper oAuth = new OAuthHelper(API_KEY, API_SECRET);
    FlickrApi flickr = new FlickrApi(oAuth);
    Token accessToken = AccessTokenUtil.readAccessToken();
    if (accessToken == null) {
      accessToken = AccessTokenUtil.getAccessToken(oAuth);
      AccessTokenUtil.writeAccessToken(accessToken);

    }
    Map<String, File> folders = new HashMap<String, File>();
    Map<String, PhotoSet> photosets = flickr.getAllSetsOfUser(accessToken);
    if (photosets == null) {
      LOG.error("Error while fetching sets of user;quitting");
      System.exit(-1);
    }
    Map<String, List<File>> folderToPhotos = new HashMap<String, List<File>>();
    sync.getAllDirs(new File(parentFolder), folders, folderToPhotos);
    for(Entry<String,String> entry:sync.skippedFolders.entrySet()){
	  System.out.println("Folder with path "+entry.getKey() + " was skipped as it had conflicting name with "+entry.getValue());
      }
    Map<String, File> foldersNotUploaded = new HashMap<String, File>();
    for (Entry<String, File> folder : folders.entrySet()) {
      if (!photosets.containsKey(folder.getKey())) {
        foldersNotUploaded.put(folder.getKey(), folder.getValue());
      }

    }
    LOG.info("All folders to be processed " + folders);
    LOG.debug("Folder Not Uploaded " + foldersNotUploaded);
    LOG.debug("Photos in each folder" + folderToPhotos);
    if (!sync.uploadFolders(flickr, foldersNotUploaded, accessToken)) {
      LOG.error("Cannot upload all new sets;Quitting");
      sync.printFinalMetrics();
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
        if (photosInSet == null) {
          LOG.error("Cannot get list of photos in set " + photoSet.getId()
              + " skipping");
          continue;
        }
        for (File photo : photosInFolder) {
          String photoName = photo.getName();
          if (!photosInSet.contains(photoName)) {
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
    sync.printFinalMetrics();
    System.out.println("Thanks for using FlickrSync");
  }

}
