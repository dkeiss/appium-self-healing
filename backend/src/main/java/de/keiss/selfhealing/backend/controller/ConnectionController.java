package de.keiss.selfhealing.backend.controller;

import de.keiss.selfhealing.backend.model.Connection;
import de.keiss.selfhealing.backend.service.ConnectionService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/connections")
@CrossOrigin(origins = {"http://localhost:8080", "http://10.0.2.2:8080"})
public class ConnectionController {

    private final ConnectionService connectionService;

    @GetMapping
    public List<Connection> searchConnections(@RequestParam @NotBlank String from, @RequestParam @NotBlank String to) {
        return connectionService.findConnections(from, to);
    }

    @GetMapping("/all")
    public List<Connection> allConnections() {
        return connectionService.findAll();
    }
}
