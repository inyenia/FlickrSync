package com.flickr.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import org.scribe.model.Request;
import org.scribe.model.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiThreadedRequestExecution implements Runnable {

  final private LinkedBlockingQueue<Request> requests;
  final private LinkedBlockingQueue<Response> responses = new LinkedBlockingQueue<Response>();
  private int numOfThreads;
  final private CallBack<Response> callback;
  private boolean shutDown = false;
  private List<Thread> threads = new ArrayList<Thread>();
  private Logger LOG = LoggerFactory.getLogger(this.getClass());

  MultiThreadedRequestExecution(int numOfThreads, CallBack<Response> callback) {
    this.callback = callback;
    this.numOfThreads = numOfThreads;
    requests = new LinkedBlockingQueue<Request>(numOfThreads);
    execute();
  }

  public void addRequest(Request request) throws InterruptedException {
    requests.put(request);
  }

  public void shutdown() {
    shutDown = true;
  }

  public boolean isComplete() {
    if (requests.size() == 0 && shutDown)
      return true;
    return false;
  }

  public void run() {
    Request request = null;
    while (!shutDown) {
      while ((request = requests.poll()) != null) {
        Response response = request.send();
        LOG.debug("Request completed");
        responses.add(response);
        if (callback != null)
          callback.call(response);
      }
    }
  }

  public Response getResponse(){
    return responses.poll();
  }

  public void waitForCompletion() {
    for (Thread t : threads) {
      try {
        t.join();
      } catch (InterruptedException e) {
        LOG.error("Error while waiting for completion", e);
      }
    }
  }

  public void execute() {

    for (int i = 0; i < numOfThreads; i++) {
      Thread t = new Thread(this);
      threads.add(t);
    }
    for (Thread thread : threads) {
      thread.start();
    }
  }

}
