package com.example.mediaserver;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class CallbackReceiver {

    @PostMapping("/notify")
    public ResponseEntity<String> handleCallback(@RequestBody Map<String, Object> body) {
        System.out.println("📥 Callback received:");
        body.forEach((k, v) -> System.out.println(k + ": " + v));
        return ResponseEntity.ok("Received");
    }
}
