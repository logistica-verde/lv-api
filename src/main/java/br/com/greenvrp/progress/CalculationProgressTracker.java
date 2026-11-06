package br.com.greenvrp.progress;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class CalculationProgressTracker {

	static final int REMEMBERED_OPERATIONS = 500;
	static final double MIN_STEP_PERCENT = 1.0;

	private final Map<Long, CalculationProgress> latest = new LinkedHashMap<>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Long, CalculationProgress> eldest) {
			return size() > REMEMBERED_OPERATIONS;
		}
	};

	private final List<Consumer<CalculationProgress>> listeners = new CopyOnWriteArrayList<>();

	public void publish(CalculationProgress progress) {
		synchronized (latest) {
			CalculationProgress previous = latest.get(progress.operationId());
			if (isRedundant(previous, progress)) {
				return;
			}
			latest.put(progress.operationId(), progress);
		}
		listeners.forEach(listener -> listener.accept(progress));
	}

	public Optional<CalculationProgress> latest(Long operationId) {
		synchronized (latest) {
			return Optional.ofNullable(latest.get(operationId));
		}
	}

	public Runnable subscribe(Consumer<CalculationProgress> listener) {
		listeners.add(listener);
		return () -> listeners.remove(listener);
	}

	private static boolean isRedundant(CalculationProgress previous, CalculationProgress next) {
		if (previous == null || previous.stage() != next.stage() || next.stage().isFinal()) {
			return false;
		}
		boolean stageDone = next.done() != null && next.done().equals(next.total());
		return !stageDone && next.percent() - previous.percent() < MIN_STEP_PERCENT;
	}

}
