package com.gamedead.server.auth;
import com.gamedead.server.error.ApiErrorWriter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;
public class AuthRequestSizeFilter extends OncePerRequestFilter {
    private static final int MAX_BYTES=8192;
    private final ApiErrorWriter errors;
    public AuthRequestSizeFilter(ApiErrorWriter errors) {this.errors=errors;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        boolean roomPost="POST".equals(request.getMethod()) && request.getServletPath().startsWith("/api/v1/rooms/");
        if(AuthEndpoints.operation(request)==null && !roomPost){chain.doFilter(request,response);return;}
        if(request.getContentLengthLong()>MAX_BYTES){reject(response);return;}
        byte[] body=request.getInputStream().readNBytes(MAX_BYTES+1);
        if(body.length>MAX_BYTES){reject(response);return;}
        chain.doFilter(new BufferedRequest(request,body),response);
    }
    private void reject(HttpServletResponse response) throws IOException {
        errors.write(response,413,"PAYLOAD_TOO_LARGE","Тело запроса превышает 8 KiB");
    }
    private static final class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private final ServletInputStream input;
        BufferedRequest(HttpServletRequest request,byte[] body) {
            super(request);this.body=body;
            var bytes=new ByteArrayInputStream(body);
            input=new ServletInputStream() {
                @Override public int read(){return bytes.read();}
                @Override public int read(byte[] buffer,int offset,int length){return bytes.read(buffer,offset,length);}
                @Override public boolean isFinished(){return bytes.available()==0;}
                @Override public boolean isReady(){return true;}
                @Override public void setReadListener(ReadListener listener){throw new IllegalStateException("Buffered JSON uses synchronous IO");}
            };
        }
        @Override public ServletInputStream getInputStream(){return input;}
        @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8));}
        @Override public int getContentLength(){return body.length;}
        @Override public long getContentLengthLong(){return body.length;}
    }
}
