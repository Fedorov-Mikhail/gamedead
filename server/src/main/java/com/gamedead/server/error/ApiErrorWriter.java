package com.gamedead.server.error;
import com.gamedead.protocol.error.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
@Component
public class ApiErrorWriter {
    private final ObjectMapper mapper;
    public ApiErrorWriter(ObjectMapper mapper){this.mapper=mapper;}
    public void write(HttpServletResponse response,int status,String code,String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control","no-store");
        response.getOutputStream().write(mapper.writeValueAsBytes(new ApiError(code,message,Map.of())));
    }
    public void unauthorized(HttpServletResponse response) throws IOException {
        write(response,401,"UNAUTHORIZED","Неверные учётные данные или недействительная сессия");
    }
}
