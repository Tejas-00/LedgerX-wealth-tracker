package com.ledgerx;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecurityProbeController {

	@GetMapping("/probe")
	public String probe() {
		return "ok";
	}

	@GetMapping("/actuator/health")
	public String health() {
		return "UP";
	}
}
