package com.testserver.test;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/test")
public class TestApi {

  @GetMapping("/load")
  public ResponseEntity<String> getLoad(@RequestParam(defaultValue = "1000") long sleepMs) throws InterruptedException {
    Thread.sleep(sleepMs);
    System.out.println("요청 성공");
    return ResponseEntity.ok("GET OK - slept " + sleepMs + "ms");
  }

  @PostMapping("/load")
  public ResponseEntity<String> postLoad(@RequestParam(defaultValue = "1000") long sleepMs) throws InterruptedException {
    Thread.sleep(sleepMs);
    System.out.println("요청 성공");
    return ResponseEntity.ok("POST OK - slept " + sleepMs + "ms");
  }
}