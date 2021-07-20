package com.example.demo;

import java.io.IOException;
import java.lang.InterruptedException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.time.ZonedDateTime;

import org.json.JSONObject;  
import org.json.JSONArray; 
import org.json.JSONException; 

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrainScheduler {

    public HttpResponse<String> sendHttpRequest(String url, HttpClient client) throws IOException, InterruptedException {
        // creates an HttpRequest object and sends the request to the given url
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMinutes(2))
            // .header("Access-Control-Allow-Origin", "*")
            .GET()
            .build();
        return client.send(request, BodyHandlers.ofString());
    }

    public JSONObject formatResults(HttpResponse<String> response) throws JSONException {
        // formats the results from the HttpResponse to make it more readable
        JSONObject results = new JSONObject();
        JSONArray arrivals = new JSONArray();
        JSONArray departures = new JSONArray();
        JSONObject json = new JSONObject(response.body());
        JSONArray data = json.getJSONArray("data");
        for (int i = 0; i < data.length(); i++) {
            JSONObject stops = data.getJSONObject(i);
            JSONObject attributes = stops.getJSONObject("attributes");
            String routeName = stops.getJSONObject("relationships").getJSONObject("route").getJSONObject("data").getString("id");
            if (routeName.startsWith("CR")) {
                // this is where you add the necessary values to the stops
                JSONObject correctStop = new JSONObject();
                correctStop.put(Constants.ARRIVAL_TIME, attributes.get(Constants.ARRIVAL_TIME));
                correctStop.put(Constants.DEPARTURE_TIME, attributes.get(Constants.DEPARTURE_TIME));
                correctStop.put("route_name", routeName);
                if (attributes.getInt("direction_id") == 0) {
                    // if direction_id is 0, it is a departure
                    departures.put(correctStop);
                } else {
                    // otherwise, it is an arrival
                    arrivals.put(correctStop);
                }
            }
        }
        results.put(Constants.ARRIVALS, arrivals);
        results.put(Constants.DEPARTURES, departures);
        return results;
    }

    public JSONArray addToJSONArray(JSONArray destination, JSONArray source) {
        // adds a JSONArray to a JSONArray
        for (int i = 0; i < source.length(); i++) {
            destination.put(source.get(i));
        }
        return destination;
    }

    public void sortCollections(List<JSONObject> arrValues, String keyName) {
        // sorts the list of JSONObjects
        Collections.sort( arrValues, new Comparator<JSONObject>() {
            // private static final String KEY_NAME = keyName;
    
            @Override
            public int compare(JSONObject a, JSONObject b) {
                String valA = new String();
                String valB = new String();
    
                try {
                    valA = (String) a.get(keyName);
                    valB = (String) b.get(keyName);
                } 
                catch (JSONException e) {
                    //do something
                }
    
                return valA.compareTo(valB);
                //if you want to change the sort order, simply use the following:
                //return -valA.compareTo(valB);
            }
        });
        // return arrValues;
    }

    @RequestMapping(value = "/api/schedules", method = RequestMethod.GET, produces = { "application/json" })
    @CrossOrigin
    // place-north is given value
    public String getSchedule(@RequestParam(name="stop", required=false) String stop) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            // url string
            String url = Constants.BASE_URL;
            if (stop != null) {
                url += "?filter[stop]=" + stop;
            } else {
                // throw error
                return null;
            }
            JSONObject results = new JSONObject();
            JSONArray arrivals = new JSONArray();
            JSONArray departures = new JSONArray();
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern(Constants.HOUR_TIME_FORMAT);
            DateTimeFormatter ymd = DateTimeFormatter.ofPattern(Constants.YEAR_DATE_FORMAT);
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of(Constants.TIME_ZONE));
            ZonedDateTime nowPlusTwoHours = now.plus(Duration.ofHours(2));
            if (ymd.format(now).equals(ymd.format(nowPlusTwoHours))) {
                url += "&filter[date]=" + ymd.format(now);
                url += "&filter[min_time]=" + dtf.format(now);
                url += "&filter[max_time]=" + dtf.format(nowPlusTwoHours);
            } else {
                // if there is a case that requires today and tomorrow, then make 2 api calls, one for today and one for tomorrow
                String tempUrl = url;
                tempUrl += "&filter[date]=" + ymd.format(nowPlusTwoHours);
                tempUrl += "&filter[max_time]=" + dtf.format(nowPlusTwoHours);
                // TODO: add error catching
                JSONObject tempObject = formatResults(sendHttpRequest(tempUrl, client));
                arrivals = addToJSONArray(arrivals, tempObject.getJSONArray(Constants.ARRIVALS));
                departures = addToJSONArray(departures, tempObject.getJSONArray(Constants.DEPARTURES));
                url += "&filter[date]=" + ymd.format(now);
                url += "&filter[min_time]=" + dtf.format(now);
            }
            // TODO: add error catching
            JSONObject tempObject2 = formatResults(sendHttpRequest(url, client));
            arrivals = addToJSONArray(arrivals, tempObject2.getJSONArray(Constants.ARRIVALS));
            departures = addToJSONArray(departures, tempObject2.getJSONArray(Constants.DEPARTURES));
            List<JSONObject> arrValues = new ArrayList<JSONObject>();
            for (int i = 0; i < arrivals.length(); i++) {
                arrValues.add(arrivals.getJSONObject(i));
            }
            sortCollections(arrValues, Constants.ARRIVAL_TIME);
            List<JSONObject> depValues = new ArrayList<JSONObject>();
            for (int i = 0; i < departures.length(); i++) {
                depValues.add(departures.getJSONObject(i));
            }
            sortCollections(depValues, Constants.DEPARTURE_TIME);
            results.put(Constants.ARRIVALS, arrValues);
            results.put(Constants.DEPARTURES, depValues);
            return results.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        } catch (JSONException e){
            e.printStackTrace();
            return null;
        }
    }
}
