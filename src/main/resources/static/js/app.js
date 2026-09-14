(function ($) {
  "use strict";

  var $form = $("#announcement-form");
  var $modalTitle = $("#announcement-modal-title");
  var $announcementId = $("#announcement-id");

  function clearFieldErrors() {
    $form.removeClass("was-validated");
    $form.find(".is-invalid").removeClass("is-invalid");
  }

  function resetAnnouncementForm() {
    if ($form.length && $form[0]) {
      $form[0].reset();
    }
    $announcementId.val("");
    $modalTitle.text("New announcement");
    clearFieldErrors();
  }

  $(function () {
    $("#new-announcement-button").on("click", resetAnnouncementForm);
    $("#announcement-modal").on("hidden.bs.modal", resetAnnouncementForm);

    $form.on("submit", function (event) {
      event.preventDefault();
    });
  });
})(jQuery);
