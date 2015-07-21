package ru.isa.ai.causal.classifiers.aq;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: Aleksandr Panov
 * Date: 30.04.2014
 * Time: 10:26
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class CRProperty implements Comparable<CRProperty> {
    @XmlElement(required = true)
    private CRFeature feature;
    @XmlElementWrapper(required = true)
    @XmlElement
    private List<Integer> indexes = new ArrayList<>();
    @XmlAttribute
    private int popularity = 0;

    public CRProperty() {
    }

    public CRProperty(CRFeature feature, List<Integer> indexes) {
        this.feature = feature;
        this.indexes = indexes;
    }

    public CRFeature getFeature() {
        return feature;
    }

    public int getPopularity() {
        return popularity;
    }

    public void setPopularity(int popularity) {
        this.popularity = popularity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CRProperty that = (CRProperty) o;

        if (!feature.equals(that.feature)) return false;
        if (indexes.size() != that.indexes.size()) return false;
        for (int value : indexes)
            if (!that.indexes.contains(value))
                return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = feature.hashCode();
        result = 31 * result + indexes.size();
        for (int value : indexes)
            result += value;
        return result;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(feature.toString());
        builder.append("=");
        for (int i = 0; i < indexes.size(); i++) {
            builder.append(indexes.get(i));
            if (i < indexes.size() - 1)
                builder.append("V");
        }
        return builder.toString();
    }

    public int collinearity(CRProperty prop) {
        if (!this.feature.equals(prop.feature)) {
            throw new IllegalArgumentException("Features must be equals");
        } else {
            if (this.indexes.containsAll(prop.indexes))
                return 1;
            else if (prop.indexes.containsAll(this.indexes))
                return -1;
            else
                return 0;
        }
    }

    public boolean cover(double value) {
        for (int index : indexes) {
            if (value >= feature.getCutPoints().get(index - 1) && value <= feature.getCutPoints().get(index))
                return true;
        }
        return false;
    }

    public boolean coverNominal(String value) {
        return indexes.contains(Integer.parseInt(value));
    }

    @Override
    public int compareTo(CRProperty o) {
        if (this.equals(o))
            return 0;
        else
            return this.getFeature().getName().compareTo(o.getFeature().getName());
    }
}

