package com.cordova.android.toolkit;

import android.os.Bundle;
import android.util.SparseArray;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.LOG;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

/**
 * *******************************************
 *
 * @Project CustomCordovaPlugin
 * @Author zs
 * @Time 2019/9/30 9:04
 * @Description Holds the pending javascript requests for the plugin
 * *******************************************
 */
public class PendingRequests {
    private static final String LOG_TAG = "PendingRequests";

    private static final String CURRENT_ID_KEY = "currentReqId";
    private static final String REQUEST_KEY_PREFIX = "request_";

    private int currentReqId = 0;
    private SparseArray<Request> requests = new SparseArray<>();

    private Bundle lastSavedState;
    private CallbackContext resumeContext;

    /**
     * Creates a request and adds it to the array of pending requests. Each created request gets a
     * unique result code for use with startActivityForResult() and requestPermission()
     *
     * @param action          The action this request corresponds to (capture image, capture audio, etc.)
     * @param options         The options for this request passed from the javascript
     * @param callbackContext The CallbackContext to return the result to
     * @return The newly created Request object with a unique result code
     * @throws JSONException
     */
    public synchronized Request createRequest(int action, JSONObject options, CallbackContext callbackContext) throws JSONException {
        Request req = new Request(action, options, callbackContext);
        requests.put(req.requestCode, req);
        return req;
    }

    /**
     * Gets the request corresponding to this request code
     *
     * @param requestCode The request code for the desired request
     * @return The request corresponding to the given request code or null if such a
     * request is not found
     */
    public synchronized Request get(int requestCode) {
        // Check to see if this request was saved
        if (lastSavedState != null && lastSavedState.containsKey(REQUEST_KEY_PREFIX + requestCode)) {
            Request r = new Request(Objects.requireNonNull(lastSavedState.getBundle(REQUEST_KEY_PREFIX + requestCode)), this.resumeContext, requestCode);
            requests.put(requestCode, r);

            // Only one of the saved requests will get restored, because that's all cordova-android
            // supports. Having more than one is an extremely unlikely scenario anyway
            this.lastSavedState = null;
            this.resumeContext = null;

            return r;
        }

        return requests.get(requestCode);
    }

    /**
     * Removes the request from the array of pending requests and sends an error plugin result
     * to the CallbackContext that contains the given error object
     *
     * @param req   The request to be resolved
     * @param error The error to be returned to the CallbackContext
     */
    public synchronized void resolveWithFailure(Request req, JSONObject error) {
        req.callbackContext.error(error);
        requests.remove(req.requestCode);
    }

    /**
     * Removes the request from the array of pending requests and sends a successful plugin result
     * to the CallbackContext that contains the result of the request
     *
     * @param req The request to be resolved
     */
    public synchronized void resolveWithSuccess(Request req) {
        req.callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, req.results));
        requests.remove(req.requestCode);
    }


    /**
     * Each request gets a unique ID that represents its request code when calls are made to
     * Activities and for permission requests
     *
     * @return A unique request code
     */
    private synchronized int incrementCurrentReqId() {
        return currentReqId++;
    }

    /**
     * Restore state saved by calling toBundle along with a callbackContext to be used in
     * delivering the results of a pending callback
     *
     * @param lastSavedState The bundle received from toBundle()
     * @param resumeContext  The callbackContext to return results to
     */
    public synchronized void setLastSavedState(Bundle lastSavedState, CallbackContext resumeContext) {
        this.lastSavedState = lastSavedState;
        this.resumeContext = resumeContext;
        this.currentReqId = lastSavedState.getInt(CURRENT_ID_KEY);
    }

    /**
     * Save the current pending requests to a bundle for saving when the Activity gets destroyed.
     *
     * @return A Bundle that can be used to restore state using setLastSavedState()
     */
    public synchronized Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putInt(CURRENT_ID_KEY, currentReqId);

        for (int i = 0; i < requests.size(); i++) {
            Request r = requests.valueAt(i);
            int requestCode = requests.keyAt(i);
            bundle.putBundle(REQUEST_KEY_PREFIX + requestCode, r.toBundle());
        }

        if (requests.size() > 1) {
            // This scenario is hopefully very unlikely because there isn't much that can be
            // done about it. Should only occur if an external Activity is launched while
            // there is a pending permission request and the device is on low memory
            LOG.w(LOG_TAG, "More than one media capture request pending on Activity destruction. Some requests will be dropped!");
        }

        return bundle;
    }

    /**
     * Holds the options and CallbackContext for a capture request made to the plugin.
     */
    public class Request {

        // Keys for use in saving requests to a bundle
        private static final String ACTION_KEY = "action";
        private static final String RESULTS_KEY = "results";

        // Unique int used to identify this request in any Android Permission or Activity callbacks
        public int requestCode;
        // The action that this request is performing
        public int action;
        // The array of results to be returned to the javascript callback on success
        public JSONArray results = new JSONArray();

        JSONObject options = new JSONObject();

        // The callback context for this plugin request
        private CallbackContext callbackContext;

        private Request(int action, JSONObject options, CallbackContext callbackContext) throws JSONException {
            this.callbackContext = callbackContext;
            this.action = action;
            this.options = options;
            this.requestCode = incrementCurrentReqId();
        }

        private Request(Bundle bundle, CallbackContext callbackContext, int requestCode) {
            this.callbackContext = callbackContext;
            this.requestCode = requestCode;
            this.action = bundle.getInt(ACTION_KEY);

            try {
                if (options != null) {
                    Set<String> keySet = bundle.keySet();
                    for (String key : keySet) {
                        this.options.put(key, bundle.getString(key));
                    }
                }
                this.results = new JSONArray(bundle.getString(RESULTS_KEY));
            } catch (JSONException e) {
                // This should never be caught
                LOG.e(LOG_TAG, "Error parsing results for request from saved bundle", e);
            }
        }

        private Bundle toBundle() {
            Bundle bundle = new Bundle();
            bundle.putInt(ACTION_KEY, this.action);
            bundle.putString(RESULTS_KEY, this.results.toString());

            try {
                if (options != null) {
                    Iterator iterator = options.keys();
                    while (iterator.hasNext()) {
                        String key = (String) iterator.next();
                        String value = options.getString(key);
                        bundle.putString(key, value);
                    }
                }
            } catch (JSONException e) {
                // This should never be caught
                LOG.e(LOG_TAG, "Error parsing results for request from saved bundle", e);
            }

            return bundle;
        }
    }
}
