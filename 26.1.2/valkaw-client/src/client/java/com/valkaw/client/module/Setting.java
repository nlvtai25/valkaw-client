package com.valkaw.client.module;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** One row in a module's Options screen. */
public final class Setting {
	public enum Type {
		CHOICE, SEGMENT, TEXT, SLIDER, BUTTON
	}

	private final String label;
	private final Type type;
	private final Supplier<String> value;
	private final Runnable cycle;
	private final Consumer<String> text;
	private final DoubleSupplier number;
	private final DoubleConsumer numberSetter;
	private final double min;
	private final double max;
	private final double step;
	private final List<String> options;
	private final IntSupplier selected;
	private final IntConsumer picker;

	private Setting(String label, Type type, Supplier<String> value, Runnable cycle, Consumer<String> text,
			DoubleSupplier number, DoubleConsumer numberSetter, double min, double max, double step,
			List<String> options, IntSupplier selected, IntConsumer picker) {
		this.label = label;
		this.type = type;
		this.value = value;
		this.cycle = cycle;
		this.text = text;
		this.number = number;
		this.numberSetter = numberSetter;
		this.min = min;
		this.max = max;
		this.step = step;
		this.options = options;
		this.selected = selected;
		this.picker = picker;
	}

	public static Setting choice(String label, Supplier<String> value, Runnable cycle) {
		return new Setting(label, Type.CHOICE, value, cycle, null, null, null, 0, 0, 0, null, null, null);
	}

	/** Two or more values shown side by side, the active one highlighted. */
	public static Setting segment(String label, List<String> options, IntSupplier selected, IntConsumer picker) {
		return new Setting(label, Type.SEGMENT, () -> options.get(selected.getAsInt()), null, null, null, null,
				0, 0, 0, List.copyOf(options), selected, picker);
	}

	public static Setting text(String label, Supplier<String> value, Consumer<String> setter) {
		return new Setting(label, Type.TEXT, value, null, setter, null, null, 0, 0, 0, null, null, null);
	}

	public static Setting slider(String label, double min, double max, double step, DoubleSupplier value,
			DoubleConsumer setter, Supplier<String> display) {
		return new Setting(label, Type.SLIDER, display, null, null, value, setter, min, max, step, null, null, null);
	}

	public static Setting button(String label, Runnable action) {
		return new Setting(label, Type.BUTTON, () -> "OPEN", action, null, null, null, 0, 0, 0, null, null, null);
	}

	public String label() {
		return label;
	}

	public Type type() {
		return type;
	}

	public String value() {
		return value.get();
	}

	public void cycle() {
		if (cycle != null) {
			cycle.run();
		}
	}

	public void setText(String text) {
		if (this.text != null) {
			this.text.accept(text);
		}
	}

	public double number() {
		return number.getAsDouble();
	}

	public void setNumber(double value) {
		if (numberSetter != null) {
			numberSetter.accept(value);
		}
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	public double step() {
		return step;
	}

	public List<String> options() {
		return options;
	}

	public int selected() {
		return selected.getAsInt();
	}

	public void pick(int index) {
		if (picker != null) {
			picker.accept(index);
		}
	}
}
