package ca.inspection.home.inspection.controller;

import ca.inspection.home.inspection.DTO.AnalyticsReport;
import ca.inspection.home.inspection.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AnalyticsController {
    @Autowired
    private AnalyticsService analyticsService;

    // from/to are "yyyy-MM"; status is ALL or a booking status; payment is ALL, PAID or UNPAID.
    @GetMapping("/analytics")
    public AnalyticsReport getAnalytics(@RequestParam YearMonth from,
                                        @RequestParam YearMonth to,
                                        @RequestParam(defaultValue = "ALL") String status,
                                        @RequestParam(defaultValue = "ALL") String payment){
        return analyticsService.build(from, to, status, payment);
    }
}
