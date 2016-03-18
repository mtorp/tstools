package dk.webbies.tscreate.analysis.declarations.typeCombiner;

import dk.webbies.tscreate.analysis.declarations.types.DeclarationType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by Erik Krogh Kristensen on 23-11-2015.
 */
public abstract class SingleTypeReducer<T extends DeclarationType, S extends DeclarationType> implements SingleTypeReducerInterface<T, S> {
    private final Map<DeclarationType, List<DeclarationType>> originals;

    protected SingleTypeReducer(Map<DeclarationType, List<DeclarationType>> originals) {
        this.originals = originals;
    }

    @Override
    public final DeclarationType reduce(T one, S two) {
        DeclarationType result = reduceIt(one, two);
        if (result == null) {
            return null;
        }
        if (result != one && result != two) {
            ArrayList<DeclarationType> originalsList = new ArrayList<>();
            assert !originals.containsKey(result);
            originals.put(result, originalsList);
            if (originals.containsKey(one)) {
                originalsList.addAll(originals.get(one));
            } else {
                originalsList.add(one);
            }
            if (originals.containsKey(two)) {
                originalsList.addAll(originals.get(two));
            } else {
                originalsList.add(two);
            }

        }
        return result;
    }

    protected abstract DeclarationType reduceIt(T one, S two);

}
