package me.stevenkin.boomvc.server.parser.http;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import me.stevenkin.boomvc.http.*;
import me.stevenkin.boomvc.mvc.AppContext;
import me.stevenkin.boomvc.server.exception.ProtocolParserException;
import me.stevenkin.boomvc.server.http.TinyHttpRequest;
import me.stevenkin.boomvc.server.http.TinyHttpResponse;
import me.stevenkin.boomvc.server.stream.SearchableByteArrayOutputStream;
import me.stevenkin.boomvc.server.parser.ProtocolParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.*;

import static me.stevenkin.boomvc.server.parser.http.ParseStatus.*;

public class HttpProtocolParser implements ProtocolParser {
    private static final Logger logger = LoggerFactory.getLogger(HttpProtocolParser.class);

    private static final byte[] LINEEND = {13, 10};

    private static final byte[] HEADERSEND = { 13, 10, 13, 10 };

    private SocketChannel socketChannel;

    private SearchableByteArrayOutputStream outputStream;

    private Queue<HttpRequest> requestQueue;

    private List<ByteBuffer> responseBuffers;

    private ByteBuffer buffer;

    private ParseStatus status;


    private byte[] line;

    private byte[] headers;

    private byte[] body;

    private ByteArrayOutputStream bodyOutputStream = new ByteArrayOutputStream();

    private int contentLength;

    private int chunkedLength;

    private boolean isChunked;

    private int offset = 0;

    private HttpRequestLine requestLine;

    private Multimap<String, HttpHeader> requestHeaders;


    private boolean isClosed = false;

    public HttpProtocolParser(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
        this.responseBuffers = new LinkedList<>();
        this.requestQueue = new LinkedList<>();
        this.outputStream = new SearchableByteArrayOutputStream();
        this.buffer = ByteBuffer.allocate(1024);
        this.status = PARSINGLINE;
        this.contentLength = -1;
        this.chunkedLength = -1;
        this.isChunked = false;
    }

    @Override
    public void parser() throws ProtocolParserException {
        boolean goon = true;
        try {
            read();
        } catch (IOException e) {
            logger.error("", e);
            throw new ProtocolParserException("a io exception happened when readed data", e);
        }
        int index = -1;
        while(goon && !this.isClosed) {
            goon = false;
            switch (this.status) {
                case PARSINGLINE:
                    index = this.outputStream.search(LINEEND);
                    if (index > -1) {
                        this.line = this.outputStream.copy(this.offset, index);
                        this.status = PARSINGHEADERS;
                        this.offset = index + 2;
                        goon = true;
                        try {
                            this.requestLine = parseHttpRequestLine(this.line);
                        } catch (UnsupportedEncodingException e) {
                            logger.error("", e);
                            throw new ProtocolParserException("url encode error", e);
                        }
                    }
                    break;
                case PARSINGHEADERS:
                    index = this.outputStream.search(HEADERSEND);
                    if (index > -1) {
                        this.headers = this.outputStream.copy(this.offset, index);
                        this.status = PARSINGBODY;
                        this.offset = index + 4;
                        goon = true;
                        try {
                            this.requestHeaders = parseHttpRequestHeaders(this.headers);
                        } catch (UnsupportedEncodingException e) {
                            logger.error("", e);
                            throw new ProtocolParserException("url encode error", e);
                        }
                    }
                    break;
                case PARSINGBODY:
                    HttpMethod httpMethod = this.requestLine.method();
                    switch (httpMethod) {
                        case GET:
                            try {
                                this.requestQueue.add(TinyHttpRequest.of(requestLine, requestHeaders, socketChannel.getRemoteAddress(), AppContext.contextPath()));
                            } catch (IOException e) {
                                logger.error("", e);
                                 throw new ProtocolParserException(e);
                            }
                            clear();
                            goon = true;
                            break;
                        case POST:
                            if (this.contentLength == -1) {
                                List<HttpHeader> headers = new ArrayList<>(this.requestHeaders.get("Content-Length"));
                                if (headers != null && headers.size() > 0) {
                                    this.contentLength = Integer.parseInt(headers.get(0).value());
                                }
                            }
                            if (this.contentLength == -1) {
                                if (!this.isChunked) {
                                    List<HttpHeader> headers = new ArrayList<>(this.requestHeaders.get("Transfer-Encoding"));
                                    if (headers != null && headers.size() > 0 && headers.get(0).value().equalsIgnoreCase("chunked")) {
                                        this.isChunked = true;
                                        this.status = PARSINGCHUNKEDLENGTH;
                                    }
                                    if (!this.isChunked)
                                        throw new ProtocolParserException();
                                }
                            }
                            if (this.contentLength > -1) {
                                if (this.outputStream.count() - this.offset >= this.contentLength) {
                                    this.body = this.outputStream.copy(this.offset, this.offset + this.contentLength);
                                    this.offset = this.offset + this.contentLength;
                                    try {
                                        this.requestQueue.add(TinyHttpRequest.of(this.requestLine, this.requestHeaders, this.body, socketChannel.getRemoteAddress(), AppContext.contextPath()));
                                    } catch (IOException e) {
                                        logger.error("", e);
                                        throw new ProtocolParserException(e);
                                    }
                                    clear();
                                    goon = true;
                                }
                                break;
                            } else {
                                switch (this.status) {
                                    case PARSINGCHUNKEDLENGTH:
                                        int index1 = this.outputStream.search(LINEEND, this.offset);
                                        if (index1 > -1) {
                                            this.chunkedLength = Integer.decode("0x" + new String(this.outputStream.copy(this.offset, index1), Charset.forName("ISO-8859-1")));
                                            this.offset = index1 + 2;
                                            this.status = PARSINGCHUNKEDBODY;
                                            goon = true;
                                        }
                                        break;
                                    case PARSINGCHUNKEDBODY:
                                        if (this.chunkedLength == 0) {
                                            //discard some data
                                            this.offset = this.outputStream.count();
                                            this.body = this.bodyOutputStream.toByteArray();
                                            try {
                                                this.requestQueue.add(TinyHttpRequest.of(this.requestLine, this.requestHeaders, this.body, socketChannel.getRemoteAddress(), AppContext.contextPath()));
                                            } catch (IOException e) {
                                                logger.error("", e);
                                                throw new ProtocolParserException(e);
                                            }
                                            clear();
                                            goon = true;
                                            break;
                                        }
                                        int index2 = this.outputStream.search(LINEEND, this.offset);
                                        if (index2 < 0)
                                            break;
                                        byte[] chunkedBody = this.outputStream.copy(this.offset, index2);
                                        if (chunkedBody.length != this.chunkedLength)
                                            throw new ProtocolParserException();
                                        try {
                                            this.bodyOutputStream.write(chunkedBody);
                                            this.offset = index2 + 2;
                                            this.status = PARSINGCHUNKEDLENGTH;
                                            goon = true;
                                        } catch (IOException e) {
                                            logger.error("", e);
                                            throw new ProtocolParserException(e);
                                        }
                                        break;
                                }
                            }
                            break;
                    }
                    break;
            }
        }

    }

    private void read() throws IOException {
        int count;
        SocketChannel channel = this.socketChannel;
        do{
            buffer.clear();
            count = channel.read(this.buffer);
            if(count < 0)
                channel.close();
            if(count > 0){
                this.outputStream.write(this.buffer.array(), 0, count);
            }
        }while (!this.buffer.hasRemaining());
    }

    private HttpRequestLine parseHttpRequestLine(byte[] lineBytes) throws UnsupportedEncodingException {
        String lineStr = new String(lineBytes, Charset.forName("ISO-8859-1"));
        lineStr = URLDecoder.decode(lineStr,"UTF-8");
        String[] strings = lineStr.split("\\s+");
        HttpRequestLine requestLine = new HttpRequestLine(HttpMethod.getHttpMethod(strings[0]), strings[1], strings[2]);
        return requestLine;
    }

    private Multimap<String, HttpHeader> parseHttpRequestHeaders(byte[] headersBytes) throws UnsupportedEncodingException {
        String headersStr = new String(headersBytes, Charset.forName("ISO-8859-1"));
        headersStr = URLDecoder.decode(headersStr,"UTF-8");
        String[] strings = headersStr.split("\r\n");
        Multimap<String, HttpHeader> headers = LinkedListMultimap.create();
        Arrays.asList(strings).forEach(s->{
            String[] strings1 = s.split(": ");
            headers.put(strings1[0], new HttpHeader(strings1[0], strings1[1]));
        });
        return headers;
    }

    private void clear(){
        List<HttpHeader> headers = new ArrayList<>(this.requestHeaders.get("Connection"));
        String s = headers.get(0).value();
        boolean b = s.equalsIgnoreCase("close");
        if(headers.size() == 1 && s.equalsIgnoreCase("close")){
            this.isClosed = true;
            return ;
        }
        this.status = PARSINGLINE;
        this.body = new byte[0];
        this.headers = new byte[0];
        this.line = new byte[0];
        this.bodyOutputStream.reset();
        this.outputStream.reset(this.offset);
        this.buffer.clear();
        this.contentLength = -1;
        this.chunkedLength = -1;
        this.isChunked = false;
        this.offset = 0;
        this.requestLine = null;
        this.requestHeaders = null;
    }

    @Override
    public boolean parsed() {
        return !this.requestQueue.isEmpty();
    }

    public HttpRequest takeHttpRequest(){
        if(!this.requestQueue.isEmpty())
            return this.requestQueue.poll();
        return null;
    }

    public ByteBuffer takeHttpResponseBuffer(){
        try {
            return this.responseBuffers.remove(0);
        } catch (IndexOutOfBoundsException e){
            return null;
        }
    }

    public HttpResponse genHttpResponse(){
        return new TinyHttpResponse();
    }

    public void putHttpResponse(HttpResponse response){
        this.responseBuffers.add(ByteBuffer.wrap(response.rawByte()));
    }

    public void putResponseBuffer(ByteBuffer buffer){
        this.responseBuffers.add(0, buffer);
    }

    public boolean isClosed() {
        return isClosed;
    }
}
