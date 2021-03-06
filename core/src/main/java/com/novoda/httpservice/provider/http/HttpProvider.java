package com.novoda.httpservice.provider.http;

import static com.novoda.httpservice.util.Log.Provider.e;
import static com.novoda.httpservice.util.Log.Provider.errorLoggingEnabled;
import static com.novoda.httpservice.util.Log.Provider.v;
import static com.novoda.httpservice.util.Log.Provider.verboseLoggingEnabled;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

import com.novoda.httpservice.Settings;
import com.novoda.httpservice.exception.ProviderException;
import com.novoda.httpservice.provider.EventBus;
import com.novoda.httpservice.provider.IntentWrapper;
import com.novoda.httpservice.provider.Provider;
import com.novoda.httpservice.provider.Response;
import com.novoda.httpservice.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;

import android.text.TextUtils;

public class HttpProvider implements Provider {

    private static final String CONTENT_TYPE = "Content-Type";

    private static final String ENCODING = "application/octet-stream";

    private static final String USER_AGENT = new UserAgent.Builder().with("HttpService").build();

    private HttpClient client;

    private EventBus eventBus;

    public HttpProvider(EventBus eventBus, Settings settings) {
        this(AndroidHttpClient.newInstance(USER_AGENT, settings), eventBus);
    }

    public HttpProvider(HttpClient client, EventBus eventBus) {
        if (eventBus == null) {
            logAndThrow("EventBus is null, can't procede");
        }
        this.eventBus = eventBus;
        this.client = client;
    }

    @Override
    public Response execute(IntentWrapper request) {
        Response response = new Response();
        final HttpParams params = new BasicHttpParams();
        HttpClientParams.setRedirecting(params, true);
        HttpUriRequest method = null;
        try {
            if (verboseLoggingEnabled()) {
                v("HttpProvider execute for : " + request.getUri());
            }
            if (request.isGet()) {
                method = new HttpGet(request.asURI());
                setContentType(method, request);
            } else if (request.isDelete()) {
                method = new HttpDelete(request.asURI());
            } else if (request.isPost()) {
                method = new HttpPost(request.asURI());
                setContentType(method, request);
                checkMultipartParams((HttpPost) method, request);
            } else if (request.isPut()) {
                method = new HttpPut(request.asURI());
                setContentType(method, request);
                checkMultipartParams((HttpPut) method, request);
            } else {
                logAndThrow("Method " + request.getMethod() + " is not implemented yet");
            }
            method.setParams(params);
            HttpContext context = new BasicHttpContext();
            eventBus.fireOnPreProcess(request, method, context);
            final HttpResponse httpResponse = client.execute(method, context);
            eventBus.fireOnPostProcess(request, httpResponse, context);
            if (httpResponse == null) {
                logAndThrow("Response from " + request.getUri() + " is null");
            }
            response.setHttpResponse(httpResponse);
            response.setIntentWrapper(request);
            if (verboseLoggingEnabled()) {
                v("Request returning response");
            }
            return response;
        } catch (Throwable t) {
            t.printStackTrace();
            eventBus.fireOnThrowable(request, t);
            if (errorLoggingEnabled()) {
                e("Problems executing the request for : " + request.getUri() + " " + t.getMessage());
            }
            return null;
        }
    }

    private void setContentType(HttpUriRequest method, IntentWrapper request) {
        String contentType = request.getContentType();
        if (contentType == null || contentType.toString().length() <= 0) {
            return;
        }
        android.util.Log.v("XXX", "setting the content type" + contentType);
        method.addHeader(CONTENT_TYPE, contentType);
    }

    private void checkMultipartParams(Object item, IntentWrapper intent) {
        String fileParamName = intent.getMultipartFileParamName();
        FileBody fileBody = getFileBodyFromFile(intent.getMultipartFile(), fileParamName);
        String uriParamName = intent.getMultipartUriParamName();
        FileBody uriBody = getFileBodyFromUri(intent.getMultipartUri(), uriParamName);
        String extraPram = intent.getMultipartExtraParam();
        StringBody stringBody = getStringBody(extraPram, intent.getMultipartExtraValue());
        String bodyEntity = intent.getBodyEntity();
        if (bodyEntity != null) {
            try {
                if (item instanceof HttpPost){
                    ((HttpPost)item).setEntity(new StringEntity(bodyEntity, HTTP.UTF_8));
                } else if (item instanceof HttpPut) {
                    ((HttpPut)item).setEntity(new StringEntity(bodyEntity, HTTP.UTF_8));
                }
            } catch (UnsupportedEncodingException e) {
                Log.e("Problem setting entity in the body", e);
            }
        } else if (stringBody != null || fileBody != null || uriBody != null) {
            MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE, null, Charset.forName(HTTP.UTF_8));
            if (stringBody != null) {
                entity.addPart(extraPram, stringBody);
            }
            if (uriBody != null) {
                entity.addPart(uriParamName, uriBody);
            }
            if (fileBody != null) {
                entity.addPart(fileParamName, fileBody);
            }
            if (item instanceof HttpPost){
                ((HttpPost)item).setEntity(entity);
            } else if (item instanceof HttpPut) {
                ((HttpPut)item).setEntity(entity);
            }
        }
    }
   

    private FileBody getFileBodyFromUri(String uri, String paramName) {
        if (TextUtils.isEmpty(paramName) || TextUtils.isEmpty(uri)) {
            return null;
        }
        File f = null;
        try {
            f = new File(new URI(uri));
        } catch (URISyntaxException e) {
            if (verboseLoggingEnabled()) {
                v("file not found " + uri);
            }
        }
        return new FileBody(f, ENCODING);
    }

    private FileBody getFileBodyFromFile(String file, String paramName) {
        if (TextUtils.isEmpty(file) || TextUtils.isEmpty(paramName)) {
            return null;
        }
        return new FileBody(new File(file), ENCODING);
    }

    private StringBody getStringBody(String param, String value) {
        if (TextUtils.isEmpty(param)) {
            return null;
        }
        if (value == null) {
            value = "";
        }
        StringBody body = null;
        try {
            body = new StringBody(value);
        } catch (Throwable t) {
            v(t.getMessage());
        }
        return body;
    }

    private void logAndThrow(String msg) {
        if (errorLoggingEnabled()) {
            e(msg);
        }
        throw new ProviderException(msg);
    }

}
