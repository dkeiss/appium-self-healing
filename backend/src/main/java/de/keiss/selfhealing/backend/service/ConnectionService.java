package de.keiss.selfhealing.backend.service;

import de.keiss.selfhealing.backend.model.Connection;
import de.keiss.selfhealing.backend.model.Connection.Leg;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;

@Service
public class ConnectionService {

    private static final String BERLIN = "Berlin Hbf";
    private static final String MUENCHEN = "München Hbf";
    private static final String HAMBURG = "Hamburg Hbf";
    private static final String STUTTGART = "Stuttgart Hbf";
    private static final String FRANKFURT = "Frankfurt Hbf";

    private static final List<Connection> CONNECTIONS = List.of(
            new Connection("1", BERLIN, MUENCHEN, LocalTime.of(6, 0), LocalTime.of(10, 2), 242, 0, 59.90,
                    List.of("ICE"),
                    List.of(new Leg(BERLIN, MUENCHEN, LocalTime.of(6, 0), LocalTime.of(10, 2), "ICE", "ICE 1001",
                            "5"))),
            new Connection("2", BERLIN, MUENCHEN, LocalTime.of(8, 30), LocalTime.of(13, 15), 285, 1, 45.50,
                    List.of("ICE", "IC"),
                    List.of(new Leg(BERLIN, "Nürnberg Hbf", LocalTime.of(8, 30), LocalTime.of(11, 45), "ICE",
                            "ICE 1005", "3"),
                            new Leg("Nürnberg Hbf", MUENCHEN, LocalTime.of(12, 0), LocalTime.of(13, 15), "IC",
                                    "IC 2023", "8"))),
            new Connection("3", BERLIN, MUENCHEN, LocalTime.of(12, 0), LocalTime.of(16, 5), 245, 0, 79.90,
                    List.of("ICE"),
                    List.of(new Leg(BERLIN, MUENCHEN, LocalTime.of(12, 0), LocalTime.of(16, 5), "ICE", "ICE 1009",
                            "7"))),
            new Connection("4", HAMBURG, STUTTGART, LocalTime.of(7, 15), LocalTime.of(13, 30), 375, 1, 69.90,
                    List.of("ICE", "ICE"),
                    List.of(new Leg(HAMBURG, "Hannover Hbf", LocalTime.of(7, 15), LocalTime.of(8, 45), "ICE", "ICE 780",
                            "12"),
                            new Leg("Hannover Hbf", STUTTGART, LocalTime.of(9, 5), LocalTime.of(13, 30), "ICE",
                                    "ICE 577", "4"))),
            new Connection("5", HAMBURG, STUTTGART, LocalTime.of(9, 0), LocalTime.of(14, 50), 350, 2, 52.00,
                    List.of("ICE", "IC", "RE"),
                    List.of(new Leg(HAMBURG, FRANKFURT, LocalTime.of(9, 0), LocalTime.of(12, 30), "ICE", "ICE 882",
                            "6"),
                            new Leg(FRANKFURT, "Mannheim Hbf", LocalTime.of(12, 45), LocalTime.of(13, 20), "IC",
                                    "IC 119", "15"),
                            new Leg("Mannheim Hbf", STUTTGART, LocalTime.of(13, 40), LocalTime.of(14, 50), "RE",
                                    "RE 4712", "2"))),
            new Connection("6", FRANKFURT, "Köln Hbf", LocalTime.of(10, 0), LocalTime.of(11, 5), 65, 0, 29.90,
                    List.of("ICE"),
                    List.of(new Leg(FRANKFURT, "Köln Hbf", LocalTime.of(10, 0), LocalTime.of(11, 5), "ICE", "ICE 123",
                            "9"))),
            new Connection("7", MUENCHEN, BERLIN, LocalTime.of(14, 0), LocalTime.of(18, 10), 250, 0, 59.90,
                    List.of("ICE"), List.of(new Leg(MUENCHEN, BERLIN, LocalTime.of(14, 0), LocalTime.of(18, 10), "ICE",
                            "ICE 1010", "11"))));

    public List<Connection> findConnections(String from, String to) {
        return CONNECTIONS.stream().filter(c -> matchesStation(c.from(), from) && matchesStation(c.to(), to)).toList();
    }

    public List<Connection> findAll() {
        return CONNECTIONS;
    }

    private boolean matchesStation(String actual, String query) {
        return actual.toLowerCase().contains(query.toLowerCase().trim());
    }
}
