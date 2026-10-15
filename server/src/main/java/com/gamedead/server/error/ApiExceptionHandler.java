package com.gamedead.server.error;
import com.gamedead.protocol.error.ApiError;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.util.Map;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(InvalidInputException.class)
    ResponseEntity<ApiError> invalid(InvalidInputException exception){
        return error(400,"INVALID_INPUT",exception.getMessage(),exception.fields());
    }
    @ExceptionHandler(LoginAlreadyTakenException.class)
    ResponseEntity<ApiError> conflict(){return error(409,"LOGIN_TAKEN","Логин уже занят",Map.of("login","Выбери другой логин"));}
    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ApiError> unauthorized(){return error(401,"UNAUTHORIZED","Неверные учётные данные или недействительная сессия",Map.of());}
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(){return error(400,"INVALID_JSON","Проверь формат JSON",Map.of());}
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(){return error(405,"METHOD_NOT_ALLOWED","Метод не поддерживается",Map.of());}
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> mediaType(){return error(415,"UNSUPPORTED_MEDIA_TYPE","Используй Content-Type application/json",Map.of());}
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> missing(){return error(404,"NOT_FOUND","Маршрут не найден",Map.of());}
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(){return error(500,"INTERNAL_ERROR","Не удалось выполнить запрос",Map.of());}
    private ResponseEntity<ApiError> error(int status,String code,String message,Map<String,String> fields){
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new ApiError(code,message,fields));
    }
}
