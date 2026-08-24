package ca.inspection.home.inspection.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

// Replaces Spring Boot's whitelabel error page.
@Controller
@Slf4j
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request) {
        int status = statusOf(request);
        String path = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Object messageAttr = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        String message = messageAttr == null || messageAttr.toString().isBlank()
                ? HttpStatus.valueOf(status).getReasonPhrase()
                : messageAttr.toString();

        if (wantsJson(request)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("timestamp", OffsetDateTime.now().toString());
            body.put("status", status);
            body.put("error", HttpStatus.valueOf(status).getReasonPhrase());
            body.put("message", message);
            body.put("path", path);
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        }

        // HTML browser — forward to the static error page.
        return "forward:/html/error.html";
    }

    private static int statusOf(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (code instanceof Integer i) return i;
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private static boolean wantsJson(HttpServletRequest request) {
        String path = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (path != null && path.startsWith("/api/")) return true;

        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE)) return true;

        String requestedWith = request.getHeader("X-Requested-With");
        return "XMLHttpRequest".equalsIgnoreCase(requestedWith);
    }
}
