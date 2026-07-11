package dev.talos.memory;

public interface EmbeddingProvider {
	int dimensions();

	float[] embed(String text);
}
