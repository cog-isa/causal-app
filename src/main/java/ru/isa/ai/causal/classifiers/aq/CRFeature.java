package ru.isa.ai.causal.classifiers.aq;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: Aleksandr Panov
 * Date: 30.07.13
 * Time: 15:17
 */
@XmlRootElement()
@XmlAccessorType(XmlAccessType.FIELD)
public class CRFeature {
    @XmlAttribute(required = true)
    private String name;
    @XmlAttribute
    private List<Double> cutPoints = new ArrayList<>();
    @XmlAttribute
    private double upLimit;
    @XmlAttribute
    private double downLimit;

    public CRFeature() {
    }

    public CRFeature(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Double> getCutPoints() {
        return cutPoints;
    }

    public void setCutPoints(List<Double> cutPoints) {
        this.cutPoints = cutPoints;
    }

    public double getUpLimit() {
        return upLimit;
    }

    public void setUpLimit(double upLimit) {
        this.upLimit = upLimit;
    }

    public double getDownLimit() {
        return downLimit;
    }

    public void setDownLimit(double downLimit) {
        this.downLimit = downLimit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CRFeature crFeature = (CRFeature) o;

        if (name != null ? !name.equals(crFeature.name) : crFeature.name != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    @Override
    public String toString() {
        return name;
    }
}
