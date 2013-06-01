FlickrSync
==========

Utility for uploading photos from PC to Flickr in an organized manner.
This utility can be used as a backup tool for all your photos on your PC and Moblie[see the recipe],
so that you never loose a photo again.


Features
========
1. Given a input parent directory it will recursively find all directories and photos and upload them onto Flickr.
2. Creates a set for each directory and add all the photos of the directory to its corresponding set.
3. Uploads all the photos in a private mode which can be seen only by the user himself/herself.
4. MultiThreaded photo upload to increase the efficiency.
5. Continuously emit all the stats showing how many photos have been uploaded in how much time,how many added to set etc.
6. Never stores user's credentials instead uses the standard OAuth Mechanism.

Usage
=====
1. This is project uses maven for it build process.Build the project using "mvn package".
2. Copy "FlickrSync-jar-with-dependencies.jar" from the "FlickrSync/target" folder to a convenient location.
3. Execute the utility "java -jar <path to FlickrSync-jar-with-dependencies.jar> [options] <path to parent folder of photos>
4. options supported are
   * -deleteFromSource Given this option,utility will delete all the photos from the source,which are also present on Flickr.
   This deletion happens before uploading of new photos.
   * -foldersToBeIgnored <comma separated list of folder>
5. System property "flickrSync.numThreads" can be set to control number of photos to be uploaded in parallel.


Recipe for Syncing Mobile photos to Flickr
==========================================
Current version of utility only supports uploading photos from PC to Flickr.
To achieve sync of photos from mobile to Flickr a third party utility such as DropBox could be installed on the phone and PC.
After all photos are uploaded to DropBox from mobile,give the folder location of DropBox on your PC this utility.
It will upload all these to Flickr thereby achieving sync of photos between mobile and Flickr.
Option of deleteFromSource can also be provided so that on each run utility deletes all photos already uploaded on Flickr,
thereby saving space on DropBox.