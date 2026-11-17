package edu.polyu.comp5511.hotelselection.service;

import edu.polyu.comp5511.hotelselection.domain.DistanceEstimate;
import edu.polyu.comp5511.hotelselection.domain.Hotel;

import java.util.List;

public interface DistanceProvider {
    List<DistanceEstimate> getRoutes(
            String origin,
            String city,
            List<String> transportModes,
            List<Hotel> hotels);
}
