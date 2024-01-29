package com.project.railway.dto;

import org.springframework.stereotype.Component;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import lombok.Data;

@Entity
@Data
@Component
public class Route {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long routeId;

	private double price;
	private String startStation; // Corrected field name
	private String endStation; // Corrected field name
	private double total_distance;

	@OneToOne
	private Train train;

}