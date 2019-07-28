package com.easyvolley;

import android.net.Uri;

import com.android.volley.Cache;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.easyvolley.dispatcher.ResponseDispatcher;
import com.easyvolley.interceptors.RequestInterceptor;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Network request builder
 *
 * @author rohitsharma
 */
public class NetworkRequestBuilder {

    // Target url of the network request
    private String mUrl;

    // Callback from the client implementation for receiving network request success/fail events.
    private Callback mCallback;

    // Request header set from the client implementation to be added in NetworkRequest
    private Map<String, String> mHeaders;

    // Uri builder for url modification. Like adding a query parameter.
    private Uri.Builder builder;

    // Network policy of the request.
    private NetworkPolicy mNetworkPolicy = NetworkPolicy.DEFAULT;

    // Form field data
    private Map<String, String> mParams;

    // Raw request body
    private byte[] mRequestBody;

    // Type of request GET|POST\PUT|DELETE. See {@link Request.Method.Type}
    private final int mRequestType;

    // Timeout for request
    private int mSocketTimeoutMs = NetworkClient.defaultSocketTimeoutMs;

    // max num of retries for the request.
    private int mMaxNumRetries = DefaultRetryPolicy.DEFAULT_MAX_RETRIES;

    // backoffMultiplier
    private float mBackoffMultiplier = DefaultRetryPolicy.DEFAULT_BACKOFF_MULT;

    // Current priority of request
    private Request.Priority mPriority = Request.Priority.NORMAL;

    /*
     * Attempt to retrieve cache for this request if available.
     * This will be used for the case of #NetworkPolicy.IGNORE_READ_BUT_WRITE_CACHE mode only.
     * It will be null for all other cases.
     */
    private Cache.Entry cachedEntry = null;

    /**
     * Priority values.  Requests will be processed from higher priorities to
     * lower priorities, in FIFO order.
     */
    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        IMMEDIATE
    }

    // Core request object
    private NetworkRequest request;

    /*package*/ NetworkRequestBuilder(String url, int requestType) {
        mUrl = url;
        mRequestType = requestType;
    }

    private String getUrl() {
        // See if builder is used for url modification if not return the url as is.
        if(builder == null) return mUrl;

        return builder.build().toString();
    }

    /**
     * Sets the raw request body for the network request.
     * @param requestBody String form of request body
     * @return current builder object
     */
    public NetworkRequestBuilder setRequestBody(String requestBody) {
        if(requestBody != null) mRequestBody = requestBody.getBytes(Charset.forName("UTF-8"));
        return this;
    }

    /**
     * Sets the raw request body for the network request.
     * @param requestBody byte[] form of request body
     * @return current builder object
     */
    public NetworkRequestBuilder setRequestBody(byte[] requestBody) {
        if(requestBody != null) mRequestBody = requestBody;

        return this;
    }

    /**
     * Add params to the network request body.
     * It is form field data pairs.
     *
     * @param params Map with multiple param entries. All will be added
     * @return current builder object
     */
    public NetworkRequestBuilder addParams(Map<String, String> params) {
        if(mParams == null) mParams = new HashMap<>();
        mParams.putAll(params);
        return this;
    }

    /**
     * Add params to the network request body.
     * Single pair of form field data pair is added.
     *
     * @param param key of form field
     * @param value value of form field
     * @return current builder object
     */
    public NetworkRequestBuilder addParams(String param, String value) {
        if(mParams == null) mParams = new HashMap<>();
        mParams.put(param, value);
        return this;
    }


    /**
     * Sets the {@link Callback} for the network request for
     * success/failure events listening.
     *
     * @param callBack Callback from client implementation.
     * @return current object
     */
    public NetworkRequestBuilder setCallback(Callback callBack) {
        if(callBack != null) mCallback = callBack;
        return this;
    }

    /**
     * Adds all the map entries into request header set.
     *
     * @param headers Map of custom headers.
     * @return current object
     */
    public NetworkRequestBuilder addHeader(Map<String, String> headers) {
        if(mHeaders == null) mHeaders = new HashMap<>();
        mHeaders.putAll(headers);

        return this;
    }

    /**
     * Add single header entry to the network request
     *
     * @param key String header key
     * @param value String header value
     * @return current builder object
     */
    public NetworkRequestBuilder addHeader(String key, String value) {
        if(mHeaders == null) mHeaders = new HashMap<>();
        mHeaders.put(key, value);

        return this;
    }

    /**
     * Add query param to the supplied url.
     *
     * @param param String query param key
     * @param value String query param value
     * @return current builder object
     */
    public NetworkRequestBuilder addQueryParam(String param, String value) {

        if(builder == null) builder = Uri.parse(mUrl).buildUpon();
        builder.appendQueryParameter(param, value);

        return this;
    }

    /**
     * Set the network policy of the request. See {@link NetworkPolicy}
     *
     * @param networkPolicy Network policy of the request.
     * @return current builder object
     */
    public NetworkRequestBuilder setNetworkPolicy(NetworkPolicy networkPolicy) {
        if(networkPolicy != null) mNetworkPolicy = networkPolicy;
        return this;
    }

    /**
     * Sets the socket timeout for this request
     *
     * @param socketTimeoutMS
     * @return current builder object
     */
    public NetworkRequestBuilder setSocketTimeoutMS(int socketTimeoutMS) {
        if(socketTimeoutMS > 0) mSocketTimeoutMs = socketTimeoutMS;
        return this;
    }

    /**
     * Set the maximum number of retry for this request.
     *
     * @param maxNumRetries maximum retry for this request.
     * @return Current network builder
     */
    public NetworkRequestBuilder setMaxNumRetries(int maxNumRetries) {
        if(maxNumRetries > 0) mMaxNumRetries = maxNumRetries;
        return this;
    }

    /**
     * Set the backoff multiplier for this request.
     *
     * @param backoffMultiplier backoff multiplier.
     * @return Current network builder
     */
    public NetworkRequestBuilder setBackoffMultiplier(float backoffMultiplier) {
        if(backoffMultiplier > 0) mBackoffMultiplier = backoffMultiplier;
        return this;
    }

    /**
     * Set the priority for this request.
     *
     * @param priority selected priority.
     * @return Current network builder
     */
    public NetworkRequestBuilder setPriority(Priority priority) {
        if(priority != null) {
            switch (priority) {
                case LOW: mPriority = Request.Priority.LOW; break;
                case NORMAL: mPriority = Request.Priority.NORMAL; break;
                case HIGH: mPriority = Request.Priority.HIGH; break;
                case IMMEDIATE: mPriority = Request.Priority.IMMEDIATE; break;
            }
        }
        return this;
    }

    /**
     * Internal Volley response handler. It will dispatch response
     * correctly to the client.
     *
     * @param response String converted network response
     */
    private void onResponse(String response) {
        ResponseDispatcher.getInstance().dispatch(mCallback, response,
            EasyVolleyResponse.fromNetworkResponse(request.getNetworkResponse()));
    }

    /**
     * Internal Volley error handler.
     *
     * @param error {@link VolleyError}
     */
    private void onError(VolleyError error) {
        if(mCallback != null) {
            mCallback.onError(EasyVolleyError.from(error));
        }

        // Check if it is NetworkPolicy.IGNORE_READ_BUT_WRITE_CACHE mode and
        // we have a cache entry removed from original cache for forcing network
        // request. As now we have received an error we are refilling it with same.
        if(cachedEntry != null) {
            NetworkClient.getRequestQueue().getCache().put(request.getCacheKey(), cachedEntry);
        }
    }

    /**
     * @param request the request to be added in network/cache queue
     */
    private void add(NetworkRequest request) {
        if(mNetworkPolicy == NetworkPolicy.OFFLINE) {
            NetworkClient.addCacheOnlyRequest(request);
            return;
        }

        // See if no cache mode is requested.
        request.setShouldCache(mNetworkPolicy != NetworkPolicy.NO_CACHE);

        if(mNetworkPolicy == NetworkPolicy.IGNORE_READ_BUT_WRITE_CACHE) {
            // Attempt to retrieve this item from cache.
            Cache.Entry entry = NetworkClient.getRequestQueue().getCache().get(request.getCacheKey());
            if (entry != null) {
                // Save cache reference locally only, it will be used in case request fails
                cachedEntry = entry;

                // Remove it from the network cache for forcing network request.
                // Incase of network failure we will refill the request with cached entry
                NetworkClient.getRequestQueue().getCache().remove(request.getCacheKey());
            }
        }

        // Enqueue it to network queue
        NetworkClient.addNetworkRequest(request);
    }

    /**
     * Execute the network request.
     */
    public NetworkRequest execute() {

        if(getUrl() == null) throw new IllegalArgumentException("Empty URL for network request");

        // Create the request
        request = new NetworkRequest(mRequestType,
                getUrl(), mHeaders, mParams, mRequestBody, this::onResponse, this::onError, mPriority);

        List<RequestInterceptor> requestInterceptors = NetworkClient.getRequestInterceptor();

        // Check for requestInterceptors if any
        for(RequestInterceptor i : requestInterceptors) {
            request = i.intercept(request);
        }

        request.setRetryPolicy(new DefaultRetryPolicy(mSocketTimeoutMs,
                mMaxNumRetries, mBackoffMultiplier));

        // Enqueue it
        add(request);

        return request;
    }

}
