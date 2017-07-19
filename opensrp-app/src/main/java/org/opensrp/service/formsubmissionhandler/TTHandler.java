package org.opensrp.service.formsubmissionhandler;

import org.opensrp.domain.form.FormSubmission;
import org.opensrp.service.MotherService;

public class TTHandler implements FormSubmissionHandler {
    private final MotherService motherService;

    public TTHandler(MotherService motherService) {
        this.motherService = motherService;
    }

    @Override
    public void handle(FormSubmission submission) {
        motherService.ttProvided(submission);
    }
}