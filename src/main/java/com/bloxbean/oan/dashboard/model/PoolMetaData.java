package com.bloxbean.oan.dashboard.model;

import java.util.Arrays;

public class PoolMetaData {
    private String version = "";
    private String logo = "";
    private String description = "";
    private String name = "";
    private String[] tags;
    private String url ="";

    private String logoUrl; //This will be derived

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    @Override
    public String toString() {
        return "PoolMetaData{" +
                "version='" + version + '\'' +
                ", logo='" + logo + '\'' +
                ", description='" + description + '\'' +
                ", name='" + name + '\'' +
                ", tags=" + Arrays.toString(tags) +
                ", url='" + url + '\'' +
                '}';
    }
}
