package com.medrassi.webapp;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Three endpoints, deliberately trivial. The app is here to be deployed. */
@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public Map<String, String> hello(@RequestParam(defaultValue = "world") String name) {
        return Map.of("message", "Hello, " + name + "!");
    }

    @GetMapping("/api/time")
    public Map<String, String> time() {
        return Map.of("time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    /**
     * Reports the machine that answered. Inside a container that is the
     * container id, so once several copies are running you can see which one
     * served each request.
     */
    @GetMapping("/api/whoami")
    public Map<String, String> whoami() throws UnknownHostException {
        return Map.of("host", InetAddress.getLocalHost().getHostName());
    }
}
