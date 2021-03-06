package me.stevenkin.boomvc.server.http;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import me.stevenkin.boomvc.http.*;
import me.stevenkin.boomvc.http.cookie.HttpCookie;
import me.stevenkin.boomvc.http.kit.StringKit;
import me.stevenkin.boomvc.server.exception.NotFoundException;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class TinyHttpResponse implements HttpResponse {

    private HttpResponseLine responseLine;

    private Multimap<String, HttpHeader> headers;

    private Map<String, HttpCookie> cookies;

    private ByteArrayOutputStream rawOutputStream;

    private ByteArrayOutputStream rawBodyOutputStream;

    private boolean isSetBody = false;

    public TinyHttpResponse() {
        this.responseLine = new HttpResponseLine();
        this.headers = LinkedListMultimap.create();
        this.cookies = new HashMap<>();
        this.rawOutputStream = new ByteArrayOutputStream();
        this.rawBodyOutputStream = new ByteArrayOutputStream();

    }

    @Override
    public HttpResponse status(int status) {
        if (this.isSetBody) throw new UnsupportedOperationException("already set body !");
        this.responseLine.status(status);
        return this;
    }

    @Override
    public HttpResponse version(String version) {
        if (this.isSetBody) throw new UnsupportedOperationException("already set body !");
        this.responseLine.httpVersion(version);
        return this;
    }

    @Override
    public HttpResponse reason(String reason) {
        if (this.isSetBody) throw new UnsupportedOperationException("already set body !");
        this.responseLine.reason(reason);
        return this;
    }


    @Override
    public HttpResponse header(String name, String value) {
        if (this.isSetBody)
            throw new UnsupportedOperationException("already set body !");
        this.headers.put(name, new HttpHeader(name, value));
        return this;
    }

    @Override
    public HttpResponse removeHeader(String name) {
        if (this.isSetBody)
            throw new UnsupportedOperationException("already set body !");
        this.headers.removeAll(name);
        return this;
    }

    @Override
    public HttpResponse cookie(String name, String value) {
        if (this.isSetBody)
            throw new UnsupportedOperationException("already set body !");
        this.cookies.put(name, new HttpCookie(name, value));
        return this;
    }

    @Override
    public HttpResponse cookie(String name, String value, int maxAge) {
        if (this.isSetBody)
            throw new UnsupportedOperationException("already set body !");
        HttpCookie cookie = new HttpCookie(name, value);
        cookie.maxAge(maxAge);
        this.cookies.put(name, new HttpCookie(name, value));
        return this;
    }

    @Override
    public HttpResponse cookie(String name, String value, int maxAge, boolean secured) {
        if (this.isSetBody)
            throw new UnsupportedOperationException("already set body !");
        HttpCookie cookie = new HttpCookie(name, value);
        cookie.maxAge(maxAge);
        cookie.secure(secured);
        this.cookies.put(name, new HttpCookie(name, value));
        return this;
    }

    @Override
    public HttpResponse cookie(String path, String name, String value, int maxAge, boolean secured) {
        if (this.isSetBody)
            throw new UnsupportedOperationException("already set body !");
        HttpCookie cookie = new HttpCookie(name, value);
        cookie.maxAge(maxAge);
        cookie.secure(secured);
        cookie.path(path);
        this.cookies.put(name, new HttpCookie(name, value));
        return this;
    }

    @Override
    public HttpResponse removeCookie(String name) {
        if (this.isSetBody)
            throw new UnsupportedOperationException("already set body !");
        this.cookies.remove(name);
        return this;
    }

    @Override
    public void body(String body) throws Exception{
        if (this.isSetBody)
            throw new UnsupportedOperationException("already set body !");
        this.rawBodyOutputStream.write(body.getBytes(Charset.forName("UTF-8")));
    }

    @Override
    public void download(File file) throws Exception {
        if (this.isSetBody)
            throw new UnsupportedOperationException("already set body !");
        if (!file.exists() || !file.isFile()) {
            throw new NotFoundException("Not found file: " + file.getPath());
        }
        String contentType = StringKit.mimeType(file.getName());
        headers.put(HttpConst.CONTENT_LENGTH, new HttpHeader(HttpConst.CONTENT_LENGTH, String.valueOf(file.length())));
        headers.put(HttpConst.CONTENT_TYPE_STRING, new HttpHeader(HttpConst.CONTENT_TYPE_STRING, contentType));
        this.rawBodyOutputStream.write(Files.readAllBytes(file.toPath()));
    }

    @Override
    public OutputStream outputStream(){
        if (this.isSetBody)
            throw new UnsupportedOperationException("already set body !");
        return this.rawBodyOutputStream;
    }

    @Override
    public void redirect(String newUri) {
        if (this.isSetBody)
            throw new UnsupportedOperationException("already set body !");
        headers.put(HttpConst.LOCATION, new HttpHeader(HttpConst.LOCATION, newUri));
        this.status(302);
    }

    @Override
    public PrintWriter writer(){
        return new PrintWriter(outputStream());
    }

    public void flush() throws Exception {
        String responseLine = this.responseLine.toString();
        StringBuilder stringBuilder = new StringBuilder(responseLine).append("\r\n");
        byte[] body = this.rawBodyOutputStream.toByteArray();
        if(!this.headers.containsKey(HttpConst.CONTENT_LENGTH))
            header(HttpConst.CONTENT_LENGTH, Integer.toString(body.length));
        cookies.values().stream().forEach(cookie->header("Set-Cookie", cookie.cookieString()));
        this.headers.values().stream().forEach(h->
                stringBuilder.append(h.toString()).append("\r\n")
        );
        stringBuilder.append("\r\n");
        this.rawOutputStream.write(stringBuilder.toString().getBytes(Charset.forName("ISO-8859-1")));
        this.rawOutputStream.write(body);
        this.isSetBody = true;
    }

    @Override
    public byte[] rawByte() {
        return this.rawOutputStream.toByteArray();
    }


}
