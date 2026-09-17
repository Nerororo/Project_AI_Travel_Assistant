package com.example.travel.region.service;

import com.example.travel.region.domain.Region;
import com.example.travel.region.dto.RegionSearchItem;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class RegionSearchService {

	private static final int EXACT_MATCH = 0;
	private static final int PREFIX_MATCH = 1;
	private static final int CONTAINS_MATCH = 2;
	private static final int NO_MATCH = Integer.MAX_VALUE;

	private final RegionCatalog catalog;

	public RegionSearchService(RegionCatalog catalog) {
		this.catalog = catalog;
	}

	public List<RegionSearchItem> search(String query) {
		if (query == null || query.isBlank()) {
			throw new IllegalArgumentException("query must not be blank");
		}

		String normalizedQuery = normalize(query.trim());
		return catalog.regions().stream()
				.map(region -> new Match(region, matchRank(region, normalizedQuery)))
				.filter(match -> match.rank() != NO_MATCH)
				.sorted(Comparator.comparingInt(Match::rank)
						.thenComparing(match -> match.region().name())
						.thenComparing(match -> provinceName(match.region()),
								Comparator.nullsFirst(Comparator.naturalOrder()))
						.thenComparing(match -> match.region().regionId()))
				.map(match -> toSearchItem(match.region()))
				.toList();
	}

	private int matchRank(Region region, String query) {
		int rank = rank(region.name(), query);
		rank = Math.min(rank, rank(region.shortName(), query));
		for (String alias : region.aliases()) {
			rank = Math.min(rank, rank(alias, query));
		}
		return rank;
	}

	private int rank(String candidate, String query) {
		String normalizedCandidate = normalize(candidate);
		if (normalizedCandidate.equals(query)) {
			return EXACT_MATCH;
		}
		if (normalizedCandidate.startsWith(query)) {
			return PREFIX_MATCH;
		}
		if (normalizedCandidate.contains(query)) {
			return CONTAINS_MATCH;
		}
		return NO_MATCH;
	}

	private RegionSearchItem toSearchItem(Region region) {
		return new RegionSearchItem(
				region.regionId(),
				region.name(),
				region.shortName(),
				provinceName(region),
				region.parentRegionId(),
				region.type(),
				region.selectable(),
				region.placeSearchFilterable()
		);
	}

	private String provinceName(Region region) {
		if (region.parentRegionId() == null) {
			return null;
		}
		return catalog.findById(region.parentRegionId())
				.orElseThrow(() -> new IllegalStateException("Validated parent region is missing"))
				.name();
	}

	private String normalize(String value) {
		return value.toLowerCase(Locale.ROOT);
	}

	private record Match(Region region, int rank) {
	}
}
