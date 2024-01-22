package com.project.railway.helper;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

	@Autowired
	SecretKeyGenerator keyGenerator;

	private static final long EXPIRATION_TIME_MS = 3600000;

	// Use Keys class to generate a secure key for HS512
	private static final byte[] SECRET_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS512).getEncoded();

	private Claims parseJwtClaims(String authToken) {
		if (authToken == null) {
			return null;
		}

		try {
			return Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(authToken).getBody();
		} catch (JwtException | IllegalArgumentException e) {
			return null;
		}
	}

	public String generateToken_for_admin(UserDetails userDetails, Date expirationDate) {
		String token = Jwts.builder().setSubject(userDetails.getUsername()).setExpiration(expirationDate)
				.signWith(SignatureAlgorithm.HS512, SECRET_KEY).compact();
		return token;
	}

	public boolean isValidToken(String authToken) {
		try {
			Claims claims = Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(authToken).getBody();
			Date expirationDate = claims.getExpiration();
			Date currentDate = new Date();
			return !expirationDate.before(currentDate);
		} catch (JwtException | IllegalArgumentException e) {
			return false;
		}
	}

}
