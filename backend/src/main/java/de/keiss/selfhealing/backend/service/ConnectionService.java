package de.keiss.selfhealing.backend.service;

import de.keiss.selfhealing.backend.model.Connection;
import de.keiss.selfhealing.backend.model.Connection.Leg;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ConnectionService {

    private static final List<Connection> CONNECTIONS = List.of(
            new Connection("1", "Berlin Hbf", "München Hbf", LocalTime.of(6, 0), LocalTime.of(10, 2), 242, 0, 59.90,
                    List.of("ICE"),
                    List.of(new Leg("Berlin Hbf", "München Hbf", LocalTime.of(6, 0), LocalTime.of(10, 2), "ICE",
                            "ICE 1001", "5"))),
            new Connection("2", "Berlin Hbf", "München Hbf", LocalTime.of(8, 30), LocalTime.of(13, 15), 285, 1, 45.50,
                    List.of("ICE", "IC"),
                    List.of(new Leg("Berlin Hbf", "Nürnberg Hbf", LocalTime.of(8, 30), LocalTime.of(11, 45), "ICE",
                            "ICE 1005", "3"),
                            new Leg("Nürnberg Hbf", "München Hbf", LocalTime.of(12, 0), LocalTime.of(13, 15), "IC",
                                    "IC 2023", "8"))),
            new Connection("3", "Berlin Hbf", "München Hbf", LocalTime.of(12, 0), LocalTime.of(16, 5), 245, 0, 79.90,
                    List.of("ICE"),
                    List.of(new Leg("Berlin Hbf", "München Hbf", LocalTime.of(12, 0), LocalTime.of(16, 5), "ICE",
                            "ICE 1009", "7"))),
            new Connection("4", "Hamburg Hbf", "Stuttgart Hbf", LocalTime.of(7, 15), LocalTime.of(13, 30), 375, 1,
                    69.90, List.of("ICE", "ICE"),
                    List.of(new Leg("Hamburg Hbf", "Hannover Hbf", LocalTime.of(7, 15), LocalTime.of(8, 45), "ICE",
                            "ICE 780", "12"),
                            new Leg("Hannover Hbf", "Stuttgart Hbf", LocalTime.of(9, 5), LocalTime.of(13, 30), "ICE",
                                    "ICE 577", "4"))),
            new Connection("5", "Hamburg Hbf", "Stuttgart Hbf", LocalTime.of(9, 0), LocalTime.of(14, 50), 350, 2, 52.00,
                    List.of("ICE", "IC", "RE"),
                    List.of(new Leg("Hamburg Hbf", "Frankfurt Hbf", LocalTime.of(9, 0), LocalTime.of(12, 30), "ICE",
                            "ICE 882", "6"),
                            new Leg("Frankfurt Hbf", "Mannheim Hbf", LocalTime.of(12, 45), LocalTime.of(13, 20), "IC",
                                    "IC 119", "15"),
                            new Leg("Mannheim Hbf", "Stuttgart Hbf", LocalTime.of(13, 40), LocalTime.of(14, 50), "RE",
                                    "RE 4712", "2"))),
            new Connection("6", "Frankfurt Hbf", "Köln Hbf", LocalTime.of(10, 0), LocalTime.of(11, 5), 65, 0, 29.90,
                    List.of("ICE"),
                    List.of(new Leg("Frankfurt Hbf", "Köln Hbf", LocalTime.of(10, 0), LocalTime.of(11, 5), "ICE",
                            "ICE 123", "9"))),
            new Connection("7", "München Hbf", "Berlin Hbf", LocalTime.of(14, 0), LocalTime.of(18, 10), 250, 0, 59.90,
                    List.of("ICE"), List.of(new Leg("München Hbf", "Berlin Hbf", LocalTime.of(14, 0),
                            LocalTime.of(18, 10), "ICE", "ICE 1010", "11"))));

    public List<Connection> findConnections(String from, String to) {
        return CONNECTIONS.stream().filter(c -> matchesStation(c.from(), from) && matchesStation(c.to(), to))
                .collect(Collectors.toList());
    }

    public List<Connection> findAll() {
        return CONNECTIONS;
    }

    private boolean matchesStation(String actual, String query) {
        return actual.toLowerCase().contains(query.toLowerCase().trim());
    }
}
