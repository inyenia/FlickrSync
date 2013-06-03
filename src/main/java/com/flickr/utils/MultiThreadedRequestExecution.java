package com.flickr.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.scribe.model.Request;
import org.scribe.model.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

public class MultiThreadedRequestExecution implements Runnable {

    final private LinkedBlockingQueue<Request> requests;
    final private LinkedBlockingQueue<Response> responses = new LinkedBlockingQueue<Response>();
    private int numOfThreads;
    final private CallBack<Response> callback;
    private boolean shutDown = false;
    private List<Thread> threads = new ArrayList<Thread>();
    private Logger LOG = LoggerFactory.getLogger(this.getClass());
    static final MetricRegistry metrics = new MetricRegistry();
    public static final Timer requestMetrics = metrics.timer(MetricRegistry
	    .name(MultiThreadedRequestExecution.class, "requests"));
    public static final Counter requestsbeingProcessed = metrics
	    .counter(MetricRegistry.name(MultiThreadedRequestExecution.class,
		    "requestsbeingProcessed"));
    private static ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
		.convertRatesTo(TimeUnit.MINUTES)
		.convertDurationsTo(TimeUnit.SECONDS).build();
    private ConsoleReporter localReporter;
    static{
	reporter.start(1, TimeUnit.MINUTES);
    }
    

    MultiThreadedRequestExecution(int numOfThreads, CallBack<Response> callback) {
	this.callback = callback;
	this.numOfThreads = numOfThreads;
	requests = new LinkedBlockingQueue<Request>(numOfThreads);
	 MetricRegistry localMetrics = new MetricRegistry();
	String gaugeName = MetricRegistry.name(
		MultiThreadedRequestExecution.class, "numOfThreads");
	localMetrics.register(gaugeName, new Gauge<Integer>() {
		@Override
		public Integer getValue() {
		    int result = 0;		    
		    for (Thread t : threads) {
			if (t.isAlive())
			    result++;
		    }
		    return result;
		}
	    });
	execute();
	localReporter  = ConsoleReporter.forRegistry(localMetrics).build();
	localReporter.start(1, TimeUnit.MINUTES);
    }

    public void addRequest(Request request) {
	try {
	    requests.put(request);
	} catch (Exception e) {
	    LOG.error("Error while waiting for submitting the request", e);
	}
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
		    try{
		    Response response;
		    final Timer.Context context = requestMetrics.time();
		    try {
		    requestsbeingProcessed.inc();
		    
			response = request.send();
			LOG.debug("Request completed");
		    } finally {
			context.stop();
			requestsbeingProcessed.dec();
		    }
		    responses.add(response);
		    if (callback != null)
			callback.call(response);
		}
	     catch (Exception e) {
		LOG.error("Error while processing request", e);
	    }
	}
    }
    }

    public Response getResponse() {
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
	localReporter.stop();
    }

    public void execute() {
	for (int i = 0; i < numOfThreads; i++) {
	    Thread t = new Thread(this);
	    t.setDaemon(true);
	    threads.add(t);
	}
	for (Thread thread : threads) {
	    thread.start();
	}
    }

}
