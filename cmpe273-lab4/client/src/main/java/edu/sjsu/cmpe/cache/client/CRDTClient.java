package edu.sjsu.cmpe.cache.client;

import java.util.*;
import java.lang.*;
import java.io.*;
import java.util.concurrent.Future;
import com.mashape.unirest.http.options.Options;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import com.mashape.unirest.http.async.Callback;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.InterruptedException;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;




public class CRDTClient implements CRDTCallbackInterface {

    private ConcurrentHashMap<String, CacheServiceInterface> serverInterface;
    private ArrayList<String> serverSuccess;
    private ConcurrentHashMap<String, ArrayList<String>> res;

    private static CountDownLatch countDownLatch;

    public CRDTClient() {

        serverInterface= new ConcurrentHashMap<String, CacheServiceInterface>(3);
        CacheServiceInterface cache0 = new DistributedCacheService("http://localhost:3000", this);
        CacheServiceInterface cache1 = new DistributedCacheService("http://localhost:3001", this);
        CacheServiceInterface cache2 = new DistributedCacheService("http://localhost:3002", this);
        serverInterface.put("http://localhost:3000", cache0);
        serverInterface.put("http://localhost:3001", cache1);
        serverInterface.put("http://localhost:3002", cache2);
    }


    @Override
    public void putFailed(Exception e) {
        System.out.println("Failed to put! ");
        countDownLatch.countDown();
    }

    @Override
    public void putCompleted(HttpResponse<JsonNode> response, String serverUrl) {
        int cd= response.getStatus();
        System.out.println( "put response success " + serverUrl);
        successServers.add(serverUrl);
        countDownLatch.countDown();
    }

    @Override
    public void getFailed(Exception e) {
        System.out.println("failed get");
        countDownLatch.countDown();
    }

    @Override
    public void getCompleted(HttpResponse<JsonNode> response, String serverUrl) {

        String val= null;
        if (response != null && response.getStatus() == 200) {
            val = response.getBody().getObject().getString("val");
                System.out.println("value of 1 in the server " + serverUrl+" " + val);
            ArrayList serverValue = res.get(val);
            if (serverValue == null) {
                serverValue = new ArrayList(3);
            }
            serverValue.add(serverUrl);
            res.put(val, serverValue);
        }

        countDownLatch.countDown();
    }
public boolean put(long key, String value) throws InterruptedException {
       serverSuccess = new ArrayList(serverInterface.size());
        countDownLatch = new CountDownLatch(serverInterface.size());

        for (CacheServiceInterface cache : serverInterface.values()) {
            cache.put(key, value);
        }

        countDownLatch.await();

        boolean isSuccess = Math.round((float)serverSuccess.size() / serverInterface.size()) == 1;

        if (! isSuccess) {
                   delete(key, value);
        }
        return isSuccess;
    }

    public void delete(long key, String value) {

        for (final String serverUrl : serverSuccess) {
            CacheServiceInterface serInt = serverInterface.get(serverUrl);
            serInt.delete(key);
        }
    }

    public String get(long key) throws InterruptedException {
        res = new ConcurrentHashMap<String, ArrayList<String>>();
        countDownLatch = new CountDownLatch(serverInterface.size());

        for (final CacheServiceInterface server : serverInterface.values()) {
            server.get(key);
        }
        countDownLatch.await();
        String correct= res.keys().nextElement();

       
        if (res.keySet().size() > 1 || res.get(correct).size() != serverInterface.size()) {
     
            ArrayList<String> valMax = TableMaxKey(res);
		if (valMax.size() == 1) {
                correct = valMax.get(0);

                ArrayList<String> repairServers = new ArrayList(serverInterface.keySet());
                repairServers.removeAll(res.get(correct));
		for (String serverUrl : repairServers) {
                System.out.println("value of 1 in the server " + serverUrl + " " + correct);
                    CacheServiceInterface server = serverInterface.get(serverUrl);
                    server.put(key, correct);

                }

            } else {
            }
        }

        return correct;

    }
 public ArrayList<String> TableMaxKey(ConcurrentHashMap<String, ArrayList<String>> table) {
        ArrayList<String> maxKeys= new ArrayList<String>();
        int maxValue = -1;
        for(Map.Entry<String, ArrayList<String>> entry : table.entrySet()) {
            if(entry.getValue().size() > maxValue) {
                maxKeys.clear();
                maxKeys.add(entry.getKey());
                maxValue = entry.getValue().size();
            }
            else if(entry.getValue().size() == maxValue)
            {
                maxKeys.add(entry.getKey());
            }
        }
        return maxKeys;
    }
}
