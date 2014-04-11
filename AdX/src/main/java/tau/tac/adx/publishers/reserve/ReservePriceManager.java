package tau.tac.adx.publishers.reserve;

import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import tau.tac.adx.util.AdxUtils;
import edu.umich.eecs.tac.util.config.ConfigProxy;

/**
 * The <b>Reserve Price Manager</b> is responsible for generating reserve price
 * according to initialization parameters and generated ones: <li>
 * {@link #dailyBaselineAverage}</li> <li>{@link #baselineRange}</li> <li>
 * {@link #updateCoefficient}</li> At the end of each day
 * {@link #updateDailyBaselineAverage()} should be called to calculate the
 * {@link #dailyBaselineAverage} for the next day.
 * 
 * @author greenwald
 * 
 */
public class ReservePriceManager {

	/**
	 * Number of digits after decimal point to keep.
	 */
	private static final int DIGITS_AFTER_DECIMAL_POINT = 4;
	/**
	 * Initial daily baseline average string. Allows configuration of
	 * {@link #dailyBaselineAverage} via a {@link ConfigProxy}.
	 */
	public static final String INITIAL_DAILY_BASELINE_AVERAGE = "INITIAL_DAILY_BASELINE_AVERAGE";
	/**
	 * A daily baseline to calculate <b>reserve price</b> according to. The
	 * initial average reserve price is randomly chosen or given, and reserve
	 * prices in subsequent days (for ads with the similar attributes) are
	 * adaptively set to maximize the publisher�s profit.
	 */
	private double dailyBaselineAverage;
	/**
	 * Allowed variance range for generated <b>reserve prices</b> by
	 * {@link #generateReservePrice()} from the {@link #dailyBaselineAverage}.
	 */
	private final double baselineRange;
	/**
	 * Determines how much the current {@link #dailyBaselineAverage} should be
	 * taken into consideration when calculating the new
	 * {@link #dailyBaselineAverage} according to the most profitable <b>reserve
	 * price</b> generated in a single day.
	 * 
	 * @see #updateDailyBaselineAverage()
	 */
	private final double updateCoefficient;
	/**
	 * Sorted map between a <b>reserve price</b> and the number of impressions
	 * it generated..
	 */
	private final TreeMap<Double, AtomicLong> profitMap = new TreeMap<Double, AtomicLong>();

	/**
	 * {@link ConfigProxy} to use.
	 */
	ConfigProxy config;

	/**
	 * @param config
	 *            {@link ConfigProxy} to cpnfigure various variables in the
	 *            class.
	 * @param baselineRange
	 *            Allowed variance range for generated <b>reserve prices</b> by
	 *            {@link #generateReservePrice()} from the
	 *            {@link #dailyBaselineAverage}.
	 * @param updateCoefficient
	 *            Determines how much the current {@link #dailyBaselineAverage}
	 *            should be taken into consideration when calculating the new
	 *            {@link #dailyBaselineAverage} according to the most profitable
	 *            <b>reserve price</b> generated in a single day.
	 */
	public ReservePriceManager(ConfigProxy config, double baselineRange,
			double updateCoefficient) {
		this.config = config;
		this.dailyBaselineAverage = config.getPropertyAsDouble(
				INITIAL_DAILY_BASELINE_AVERAGE, 0);
		this.baselineRange = baselineRange;
		this.updateCoefficient = updateCoefficient;
	}

	/**
	 * @param dailyBaselineAverage
	 *            A daily baseline to calculate <b>reserve price</b> according
	 *            to. The initial average reserve price is given, and reserve
	 *            prices in subsequent days (for ads with the similar
	 *            attributes) are adaptively set to maximize the publisher�s
	 *            profit.
	 * @param baselineRange
	 *            Allowed variance range for generated <b>reserve prices</b> by
	 *            {@link #generateReservePrice()} from the
	 *            {@link #dailyBaselineAverage}.
	 * @param updateCoefficient
	 *            Determines how much the current {@link #dailyBaselineAverage}
	 *            should be taken into consideration when calculating the new
	 *            {@link #dailyBaselineAverage} according to the most profitable
	 *            <b>reserve price</b> generated in a single day.
	 */
	public ReservePriceManager(double dailyBaselineAverage,
			double baselineRange, double updateCoefficient) {
		this.dailyBaselineAverage = dailyBaselineAverage;
		this.baselineRange = baselineRange;
		this.updateCoefficient = updateCoefficient;
	}

	/**
	 * @return A random reserve price according to the <b>daily baseline
	 *         average</b> and the <b>baseline range</b>.
	 */
	public double generateReservePrice() {
		return AdxUtils.cutDouble(Math.random() * baselineRange
				* DIGITS_AFTER_DECIMAL_POINT + dailyBaselineAverage
				- baselineRange, DIGITS_AFTER_DECIMAL_POINT);
	}

	/**
	 * Updates the {@link ReservePriceManager} with data about how many
	 * Impressions were generated by a given <b>reserve price</b>.
	 * 
	 * @param reservePrice
	 *            Reserve price to update data for.
	 */
	public void addImpressionForPrice(double reservePrice) {
		if (!profitMap.containsKey(reservePrice)) {
			profitMap.put(reservePrice, new AtomicLong());
		}
		profitMap.get(reservePrice).incrementAndGet();
	}

	/**
	 * Updates the {@link #dailyBaselineAverage} in the direction of the reserve
	 * price that resulted in the highest profits during the day, according to
	 * the {@link #updateCoefficient}.<br>
	 * {@link #dailyBaselineAverage}= {@link #updateCoefficient} *
	 * dailyBaselineAverage + (1 - updateCoefficient) *
	 * highestProfitsReservePrice;
	 * 
	 * @return Updated {@link #dailyBaselineAverage}.
	 */
	public double updateDailyBaselineAverage() {
		double highestProfitsPrice = getMostProfitableReservePrice();
		profitMap.clear();
		dailyBaselineAverage = updateCoefficient * dailyBaselineAverage
				+ (1 - updateCoefficient) * highestProfitsPrice;
		return dailyBaselineAverage;
	}

	/**
	 * Calculates the most profitable reserve price.
	 * 
	 * @return The most profitable reserve price.
	 */
	public double getMostProfitableReservePrice() {
		double bestReservePrice = 0;
		long bestReservePriceImpresssions = 0;
		for (Entry<Double, AtomicLong> entry : profitMap.entrySet()) {
			if (entry.getKey() * entry.getValue().get() > bestReservePrice
					* bestReservePriceImpresssions) {
				bestReservePrice = entry.getKey();
				bestReservePriceImpresssions = entry.getValue().get();
			}
		}
		return bestReservePrice;
	}

	/**
	 * @return the dailyBaselineAverage
	 */
	public double getDailyBaselineAverage() {
		return dailyBaselineAverage;
	}

}
