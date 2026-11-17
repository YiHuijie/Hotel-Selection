package edu.polyu.comp5511.hotelselection.agent;

import edu.polyu.comp5511.hotelselection.domain.MemoryPreference;
import java.util.List;

public interface UserPreferenceMemory {
    List<MemoryPreference> refresh(String userId);

    List<MemoryPreference> refresh(String userId, ModelCallCounter counter);
}
