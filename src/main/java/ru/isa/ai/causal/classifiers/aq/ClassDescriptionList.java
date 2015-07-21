package ru.isa.ai.causal.classifiers.aq;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Author: Aleksandr Panov
 * Date: 17.12.2014
 * Time: 10:37
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class ClassDescriptionList {
    @XmlElementWrapper
    @XmlElement
    private List<AQClassDescription> classDescriptions = new ArrayList<>();

    public ClassDescriptionList() {
    }

    public ClassDescriptionList(List<AQClassDescription> classDescriptions) {
        this.classDescriptions = classDescriptions;
    }

    public ClassDescriptionList(Collection<AQClassDescription> classDescriptions) {
        this.classDescriptions.addAll(classDescriptions);
    }

    public List<AQClassDescription> getClassDescriptions() {
        return classDescriptions;
    }

    public void setClassDescriptions(List<AQClassDescription> classDescriptions) {
        this.classDescriptions = classDescriptions;
    }
}
