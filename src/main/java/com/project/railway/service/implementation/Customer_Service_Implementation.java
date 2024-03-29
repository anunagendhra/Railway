package com.project.railway.service.implementation;

import java.io.IOException;
import java.net.InetAddress;
import java.sql.Date;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.project.railway.dto.Admin;
import com.project.railway.dto.Customer;
import com.project.railway.helper.JwtUtil;
import com.project.railway.helper.ResponseStructure;
import com.project.railway.helper.Sms_Service;
import com.project.railway.repository.Customer_Repository;
import com.project.railway.service.Customer_Service;

import freemarker.template.MalformedTemplateNameException;
import freemarker.template.TemplateNotFoundException;
import jakarta.mail.internet.ParseException;

@Service
public class Customer_Service_Implementation implements Customer_Service {

	@Autowired
	private Customer_Repository customer_Repository;

	@Autowired
	private BCryptPasswordEncoder encoder;

	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	JwtUtil jwtUtil;

	@Autowired
	Sms_Service sms_Service;

	public ResponseEntity<ResponseStructure<Customer>> signup(Customer customer, MultipartFile pic) throws Exception {

		ResponseStructure<Customer> structure = new ResponseStructure<>();

		customer.setPassword(encoder.encode(customer.getPassword()));
		byte[] picture = new byte[pic.getBytes().length];
		pic.getInputStream().read(picture);
		customer.setPhoto(picture);

		// Check for duplicate email or mobile
		if (customer_Repository.findByEmail(customer.getEmail()) != null
				|| customer_Repository.findByMobile(customer.getMobile()) != null) {
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
			structure.setMessage("Email or Mobile should not be repeated");
			return ResponseEntity.badRequest().body(structure);
		} else {
			boolean sms = sms_Service.smsSent(customer);
			if (sms) {
				customer.setRole("customer");
				customer_Repository.save(customer);

				structure.setData2(customer);
				structure.setStatus(HttpStatus.CREATED.value());
				structure.setMessage("OTP sent successfully via SMS");

				return ResponseEntity.ok(structure);
			} else {
				structure.setData(null);
				structure.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
				structure.setMessage("Failed to send OTP via SMS. Twilio status: ");
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(structure);

			}
		}
	}

	@Override
	public ResponseEntity<ResponseStructure<Customer>> login(String email, String password)
			throws TemplateNotFoundException, MalformedTemplateNameException, ParseException, IOException {
		ResponseStructure<Customer> structure = new ResponseStructure<>();
		Customer customer = customer_Repository.findByEmail(email);
		if (customer == null) {
			structure.setData(null);
			structure.setMessage("No User, Create Your Account");
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
			return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
		} else {

			UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(email, password);

			Authentication authentication = authenticationManager.authenticate(authToken);
			SecurityContextHolder.getContext().setAuthentication(authentication);

			UserDetails userDetails = (UserDetails) authentication.getPrincipal();

			if (userDetails != null) {
				long expirationMillis = System.currentTimeMillis() + 3600000; // 1 hour in milliseconds
				Date expirationDate = new Date(expirationMillis);
				String token = jwtUtil.generateToken_for_admin(userDetails, expirationDate);
				structure.setData(token);
				structure.setMessage("Login Success");
				structure.setStatus(HttpStatus.OK.value());
			}
			return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
		}
	}

	@Override
	public ResponseEntity<ResponseStructure<Customer>> verifyotp(String email, int otp) {
		ResponseStructure<Customer> structure = new ResponseStructure<>();
		Customer customer = customer_Repository.findByEmail(email);
		if (customer != null && customer.getOtp() == otp) {
			if (sms_Service.isOtpValid(customer)) {
				customer.setStatus(true);
				customer.setOtp(0);
				customer.setSetOtpGeneratedTime(null);
				customer_Repository.save(customer);
				structure.setData2(customer);
				structure.setStatus(HttpStatus.OK.value());
				structure.setMessage("OTP Verified Successfully");
				return new ResponseEntity<>(structure, HttpStatus.OK);
			} else {
				structure.setData(null);
				structure.setStatus(HttpStatus.BAD_REQUEST.value());
				structure.setMessage("OTP has expired.");
				return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
			}
		} else {
			structure.setData(null);
			structure.setMessage("Otp Not Verified Sucessfully");
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
			return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
		}
	}

	@Override
	public ResponseEntity<ResponseStructure<Customer>> forgot_passowrd(String email) throws Exception {
		ResponseStructure<Customer> structure = new ResponseStructure<>();
		Customer customer = customer_Repository.findByEmail(email);
		if (customer == null) {
			structure.setData2(customer);
			structure.setMessage(customer.getEmail() + "Email doesn't exits,create account first");
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
			return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
		} else {
			int otp = new Random().nextInt(100000, 999999);
			customer.setOtp(otp);
			customer.setSetOtpGeneratedTime(LocalDateTime.now());

			if (sms_Service.smsSent(customer)) {
				customer_Repository.save(customer);
				structure.setData2(customer);
				structure.setStatus(HttpStatus.OK.value());
				structure.setMessage(customer.getEmail() + "OTP send succesfull,check once");
				return new ResponseEntity<>(structure, HttpStatus.OK);
			} else {
				structure.setData(null);
				structure.setStatus(HttpStatus.NOT_ACCEPTABLE.value());
				structure.setMessage("Something went Wrong, Check email and try again");
				return new ResponseEntity<>(structure, HttpStatus.NOT_ACCEPTABLE);
			}
		}

	}

	@Override
	public ResponseEntity<ResponseStructure<Customer>> submitForgotOtp(String email, int otp) {
		ResponseStructure<Customer> structure = new ResponseStructure<>();
		Customer customer = customer_Repository.findByEmail(email);

		if (customer != null && customer.getOtp() == otp) {
			LocalDateTime otpGeneratedTime = customer.getSetOtpGeneratedTime();
			LocalDateTime currentTime = LocalDateTime.now();
			Duration duration = Duration.between(otpGeneratedTime, currentTime);

			if (duration.toMinutes() <= 5) {
				customer.setStatus(true);
				customer.setOtp(0);
				long expirationMillis = System.currentTimeMillis() + 600000; // 10 min in milliseconds
				Date expirationDate = new Date(expirationMillis);
				String token = jwtUtil.generateToken_for_customer(customer, expirationDate);
				customer_Repository.save(customer);
				structure.setData(token);
				structure.setData2(customer);
				structure.setMessage("Account Verified Successfully");
				structure.setStatus(HttpStatus.ACCEPTED.value());
			} else {
				structure.setData(null);
				structure.setMessage("OTP has expired.");
				structure.setStatus(HttpStatus.BAD_REQUEST.value());
			}
		} else {
			structure.setData(null);
			structure.setMessage("Incorrect OTP");
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
		}

		return new ResponseEntity<>(structure, HttpStatus.OK);
	}

	@Override
	public ResponseEntity<ResponseStructure<Customer>> setPassword(String email, String password, String token) {
		ResponseStructure<Customer> structure = new ResponseStructure<>();
		Customer customer = customer_Repository.findByEmail(email);
		if (!jwtUtil.isValidToken(token)) {
			structure.setData(null);
			structure.setMessage("Some thing Went Wrong");
			structure.setStatus(HttpStatus.BAD_REQUEST.value());
			return new ResponseEntity<>(structure, HttpStatus.BAD_REQUEST);
		} else {
			customer.setPassword(encoder.encode(password));
			customer_Repository.save(customer);
			structure.setData2(customer);
			structure.setMessage("Password Reset Success");
			structure.setStatus(HttpStatus.OK.value());
			return new ResponseEntity<>(structure, HttpStatus.OK);
		}
	}
}
