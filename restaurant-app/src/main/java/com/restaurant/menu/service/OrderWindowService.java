package com.restaurant.menu.service;

import com.restaurant.shared.exception.OrderWindowClosedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;

@Service
public class OrderWindowService {

    private final LocalTime defaultStart;
    private final LocalTime defaultEnd;
    private final ZoneId zoneId;

    public OrderWindowService(
            @Value("${canteen.order.start:11:00}") String start,
            @Value("${canteen.order.end:14:30}") String end,
            @Value("${canteen.order.zone:Asia/Hong_Kong}") String zone) {
        this.defaultStart = LocalTime.parse(start);
        this.defaultEnd = LocalTime.parse(end);
        this.zoneId = ZoneId.of(zone);
    }

    public boolean isOpen() {
        LocalTime now = LocalTime.now(zoneId);
        return !now.isBefore(defaultStart) && !now.isAfter(defaultEnd);
    }

    public void assertOpen() {
        if (!isOpen()) {
            throw new OrderWindowClosedException(
                    "Ordering is only available between " + defaultStart + " and " + defaultEnd);
        }
    }

    public ZoneId getZoneId() {
        return zoneId;
    }
}
