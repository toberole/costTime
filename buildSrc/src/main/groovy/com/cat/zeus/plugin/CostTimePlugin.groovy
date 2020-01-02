package com.cat.zeus.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project


class CostTimePlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.android.registerTransform(new CostTimeTransform(project))
    }
}