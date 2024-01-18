package com.project.railway.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.project.railway.dto.Admin;

public interface Admin_Repository extends JpaRepository<Admin, Integer> {

	@Query("SELECT COUNT(a) FROM Admin a")
	int countByUsernameAndPassword(String username, String password);
}
