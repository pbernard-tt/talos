package dev.talos.memory;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 13 embedding provider. Revision 2.1 deliberately does not name an external embeddings
 * API. This deterministic lexical provider keeps ingestion/retrieval functional and testable
 * without introducing a subscription credential; the provider boundary is where a BYOK external
 * implementation can be added once the concrete provider contract is chosen.
 */
@Component
public class HashEmbeddingProvider implements EmbeddingProvider {

	public static final int DIMENSIONS = 64;
	private static final Pattern TOKEN = Pattern.compile("[a-z0-9_]+");

	@Override
	public int dimensions() {
		return DIMENSIONS;
	}

	@Override
	public float[] embed(String text) {
		float[] vector = new float[DIMENSIONS];
		Matcher matcher = TOKEN.matcher((text == null ? "" : text).toLowerCase(Locale.ROOT));
		while (matcher.find()) {
			String token = matcher.group();
			int index = Math.floorMod(hash(token), DIMENSIONS);
			vector[index] += 1.0f;
		}
		normalize(vector);
		return vector;
	}

	private int hash(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
			return ((bytes[0] & 0xff) << 24) | ((bytes[1] & 0xff) << 16) | ((bytes[2] & 0xff) << 8)
					| (bytes[3] & 0xff);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required by the JDK", e);
		}
	}

	private void normalize(float[] vector) {
		double sumSquares = 0.0;
		for (float value : vector) {
			sumSquares += value * value;
		}
		if (sumSquares == 0.0) {
			return;
		}
		float magnitude = (float) Math.sqrt(sumSquares);
		for (int i = 0; i < vector.length; i++) {
			vector[i] = vector[i] / magnitude;
		}
	}
}
