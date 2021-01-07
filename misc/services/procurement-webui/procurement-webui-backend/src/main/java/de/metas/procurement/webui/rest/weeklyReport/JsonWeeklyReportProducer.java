/*
 * #%L
 * procurement-webui-backend
 * %%
 * Copyright (C) 2021 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package de.metas.procurement.webui.rest.weeklyReport;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import de.metas.procurement.webui.model.BPartner;
import de.metas.procurement.webui.model.Product;
import de.metas.procurement.webui.model.ProductSupply;
import de.metas.procurement.webui.model.Trend;
import de.metas.procurement.webui.model.User;
import de.metas.procurement.webui.model.WeekSupply;
import de.metas.procurement.webui.service.IProductSuppliesService;
import de.metas.procurement.webui.util.DateUtils;
import de.metas.procurement.webui.util.YearWeekUtil;
import lombok.Builder;
import lombok.NonNull;
import org.threeten.extra.YearWeek;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Extract daily supply reports and produces the aggregated {@link JsonWeeklyReport}
 */
class JsonWeeklyReportProducer
{
	@NonNull
	private final IProductSuppliesService productSuppliesService;

	@NonNull
	private final User user;

	@NonNull
	private final Locale locale;

	@NonNull
	private final YearWeek week;

	@Builder
	private JsonWeeklyReportProducer(
			@NonNull final IProductSuppliesService productSuppliesService,
			@NonNull final User user,
			@NonNull final Locale locale,
			@NonNull final YearWeek week)
	{
		this.productSuppliesService = productSuppliesService;
		this.user = user;
		this.locale = locale;
		this.week = week;
	}

	public JsonWeeklyReport execute()
	{
		final BPartner bpartner = user.getBpartner();

		final LocalDate startDate = week.atDay(DayOfWeek.MONDAY);
		final LocalDate endDate = week.atDay(DayOfWeek.SUNDAY);
		final List<LocalDate> days = DateUtils.getDaysList(startDate, endDate);

		//
		final List<Product> favoriteProducts = productSuppliesService.getUserFavoriteProducts(user);
		final ImmutableMap<Long, Product> favoriteProductsById = Maps.uniqueIndex(favoriteProducts, Product::getId);

		//
		final List<ProductSupply> dailySupplies = productSuppliesService.getProductSupplies(
				bpartner.getId(),
				-1, // all products
				startDate,
				endDate);
		final ImmutableListMultimap<Long, ProductSupply> dailySuppliesByProductId = Multimaps.index(dailySupplies, dailySupply -> dailySupply.getProduct().getId());

		//
		final ImmutableMap<Long, WeekSupply> nextWeekForcastsByProductId = Maps.uniqueIndex(
				productSuppliesService.getWeeklySupplies(bpartner, week.plusWeeks(1)),
				weekSupply -> weekSupply.getProduct().getId());

		final ImmutableSet<Long> productIds = ImmutableSet.<Long>builder()
				.addAll(dailySuppliesByProductId.keySet())
				.addAll(favoriteProductsById.keySet())
				.build();
		final ArrayList<JsonWeeklyProductReport> resultProducts = new ArrayList<>(productIds.size());
		for (final Long productId : productIds)
		{
			final WeekSupply nextWeekForecast = nextWeekForcastsByProductId.get(productId);
			final Trend nextWeekTrend = nextWeekForecast != null ? nextWeekForecast.getTrend() : null;

			final ImmutableList<ProductSupply> productDailySupplies = dailySuppliesByProductId.get(productId);
			if (productDailySupplies.isEmpty())
			{
				final Product product = favoriteProductsById.get(productId);
				resultProducts.add(JsonWeeklyProductReport.builder()
						.productId(product.getIdAsString())
						.productName(product.getName(locale))
						.packingInfo(product.getPackingInfo(locale))
						.dailyQuantities(JsonDailyProductQtyReport.zero(days, locale))
						.nextWeekTrend(nextWeekTrend)
						.build());
			}
			else
			{
				final ImmutableMap<LocalDate, ProductSupply> productDailySuppliesByDate = Maps.uniqueIndex(productDailySupplies, ProductSupply::getDay);
				final ArrayList<JsonDailyProductQtyReport> resultDailyQtys = new ArrayList<>(days.size());
				for (final LocalDate date : days)
				{
					final ProductSupply productSupply = productDailySuppliesByDate.get(date);
					if (productSupply != null)
					{
						resultDailyQtys.add(JsonDailyProductQtyReport.builder()
								.date(date)
								.dayCaption(DateUtils.getDayName(date, locale))
								.qty(productSupply.getQty())
								.build());
					}
					else
					{
						resultDailyQtys.add(JsonDailyProductQtyReport.zero(date, locale));
					}
				}

				final Product product = productDailySupplies.get(0).getProduct();
				resultProducts.add(JsonWeeklyProductReport.builder()
						.productId(product.getIdAsString())
						.productName(product.getName(locale))
						.packingInfo(product.getPackingInfo(locale))
						.dailyQuantities(resultDailyQtys)
						.nextWeekTrend(nextWeekTrend)
						.build());
			}
		}

		resultProducts.sort(Comparator.comparing(JsonWeeklyProductReport::getProductName));

		return JsonWeeklyReport.builder()
				.week(YearWeekUtil.toJsonString(week))
				.weekCaption(YearWeekUtil.toDisplayName(week))
				.previousWeek(YearWeekUtil.toJsonString(week.minusWeeks(1)))
				.nextWeek(YearWeekUtil.toJsonString(week.plusWeeks(1)))
				.nextWeekCaption(YearWeekUtil.toDisplayName(week.plusWeeks(1)))
				.products(resultProducts)
				.build();
	}
}
