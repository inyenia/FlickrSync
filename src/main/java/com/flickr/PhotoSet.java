package com.flickr;

public class PhotoSet implements Comparable<PhotoSet> {
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((title == null) ? 0 : title.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    PhotoSet other = (PhotoSet) obj;
    if (title == null) {
      if (other.title != null)
        return false;
    } else if (!title.equals(other.title))
      return false;
    return true;
  }

  @Override
  public String toString() {
    return "PhotoSet [id=" + id + ", numOfPhotos=" + numOfPhotos
        + ", numOfVidoes=" + numOfVidoes + ", title=" + title + "]";
  }

  public PhotoSet(String id, int numOfPhotos, int numOfVidoes, String title) {
    super();
    this.id = id;
    this.numOfPhotos = numOfPhotos;
    this.numOfVidoes = numOfVidoes;
    this.title = title;
  }

  private String id;
  private int numOfPhotos;
  private int numOfVidoes;
  private String title;

  public String getId() {
    return id;
  }

  public int getNumOfPhotos() {
    return numOfPhotos;
  }

  public int getNumOfVidoes() {
    return numOfVidoes;
  }

  public String getTitle() {
    return title;
  }

  public int compareTo(PhotoSet o) {
    // TODO Auto-generated method stub
    return this.title.compareTo(o.title);
  }

}
