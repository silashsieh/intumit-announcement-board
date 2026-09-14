(function ($) {
  "use strict";

  var ANNOUNCEMENTS_API = "api/announcements";
  var PAGE_SIZE = 10;
  var PAGINATION_WINDOW = 5;

  var FIELD_SELECTORS = {
    title: "#announcement-title",
    publisher: "#announcement-publisher",
    publishDate: "#announcement-publish-date",
    deadlineDate: "#announcement-deadline-date",
    content: "#announcement-content"
  };

  var currentPage = 0;
  var currentItemCount = 0;
  var listRequestToken = 0;
  var editRequestToken = 0;
  var listRequest = null;
  var saveRequest = null;
  var editRequest = null;
  var deleteRequest = null;
  var pendingDeleteId = null;
  var lastListTrigger = null;

  var $form = $("#announcement-form");
  var $modalTitle = $("#announcement-modal-title");
  var $announcementId = $("#announcement-id");
  var $title = $("#announcement-title");
  var $publisher = $("#announcement-publisher");
  var $publishDate = $("#announcement-publish-date");
  var $deadlineDate = $("#announcement-deadline-date");
  var $content = $("#announcement-content");
  var $saveButton = $("#save-announcement-button");
  var $formError = $("#announcement-form-error");
  var $feedback = $("#announcement-feedback");
  var $loading = $("#announcement-loading");
  var $empty = $("#announcement-empty");
  var $table = $("#announcement-table");
  var $tbody = $("#announcement-table-body");
  var $pagination = $("#announcement-pagination");
  var $deleteBody = $("#delete-modal-body");
  var $confirmDelete = $("#confirm-delete-button");
  var $announcementModal = $("#announcement-modal");
  var $deleteModal = $("#delete-modal");

  function announcementUrl(id) {
    return ANNOUNCEMENTS_API + "/" + encodeURIComponent(id);
  }

  function asText(value) {
    if (value == null) {
      return "";
    }
    return String(value);
  }

  function getModal(el) {
    return bootstrap.Modal.getOrCreateInstance(el);
  }

  function showFeedback(message, isError) {
    $feedback
      .removeClass("d-none alert-success alert-danger alert-info")
      .addClass(isError ? "alert-danger" : "alert-success")
      .attr("role", isError ? "alert" : "status")
      .attr("aria-live", isError ? "assertive" : "polite")
      .text(asText(message));
  }

  function hideFeedback() {
    $feedback.addClass("d-none").text("");
  }

  function showFormError(message) {
    $formError.removeClass("d-none").text(asText(message));
  }

  function hideFormError() {
    $formError.addClass("d-none").text("");
  }

  function setListBusy(isBusy) {
    $table.attr("aria-busy", isBusy ? "true" : "false");
    $("#main-content").attr("aria-busy", isBusy ? "true" : "false");
    if (isBusy) {
      $loading.removeClass("d-none");
    } else {
      $loading.addClass("d-none");
    }
  }

  function setSaving(isSaving) {
    $saveButton.prop("disabled", isSaving);
    $form.find("input:not([type='hidden']), textarea").prop("disabled", isSaving);
  }

  function clearFieldErrors() {
    $form.removeClass("was-validated");
    $form.find(".is-invalid").removeClass("is-invalid");
    $form.find(".invalid-feedback").each(function () {
      var $feedbackEl = $(this);
      var defaultMessage = $feedbackEl.data("defaultMessage");
      if (defaultMessage) {
        $feedbackEl.text(defaultMessage);
      }
    });
  }

  function setFieldInvalid($input, message) {
    $input.addClass("is-invalid");
    var describedBy = $input.attr("aria-describedby");
    if (describedBy) {
      $("#" + describedBy).text(asText(message));
    }
  }

  function firstInvalidField() {
    return $form.find(".is-invalid").filter("input, textarea").first();
  }

  function syncDeadlineMin() {
    var publish = $publishDate.val();
    if (publish) {
      $deadlineDate.attr("min", publish);
    } else {
      $deadlineDate.removeAttr("min");
    }
  }

  function resetAnnouncementForm() {
    if ($form.length && $form[0]) {
      $form[0].reset();
    }
    $announcementId.val("");
    $modalTitle.text("New announcement");
    $deadlineDate.removeAttr("min");
    clearFieldErrors();
    hideFormError();
    setSaving(false);
  }

  function parseError(jqXHR) {
    var result = {
      message: "The request could not be completed.",
      fieldErrors: {}
    };

    if (jqXHR && jqXHR.status === 0) {
      result.message = "Network error. Please try again.";
      return result;
    }

    var data = jqXHR && jqXHR.responseJSON;
    if (data && typeof data === "object") {
      if (typeof data.message === "string" && data.message) {
        result.message = data.message;
      }
      if (data.fieldErrors && typeof data.fieldErrors === "object") {
        $.each(data.fieldErrors, function (field, message) {
          if (typeof message === "string" && message) {
            result.fieldErrors[field] = message;
          }
        });
      }
    }

    if (jqXHR && jqXHR.status === 404) {
      if (!(data && typeof data.message === "string" && data.message)) {
        result.message = "Announcement not found.";
      }
    } else if (jqXHR && jqXHR.status >= 500) {
      result.message = "An unexpected error occurred.";
    }

    return result;
  }

  function applyFieldErrors(fieldErrors) {
    var mapped = false;
    $.each(fieldErrors, function (field, message) {
      var selector = FIELD_SELECTORS[field];
      if (!selector) {
        return;
      }
      setFieldInvalid($(selector), message);
      mapped = true;
    });
    var $first = firstInvalidField();
    if ($first.length) {
      $first.trigger("focus");
    }
    return mapped;
  }

  function validateForm() {
    clearFieldErrors();
    hideFormError();

    var title = $.trim($title.val());
    var publisher = $.trim($publisher.val());
    var content = $.trim($content.val());
    var publish = $publishDate.val();
    var deadline = $deadlineDate.val();

    if (!title) {
      setFieldInvalid($title, "Title is required and must not exceed 200 characters.");
    } else if (title.length > 200) {
      setFieldInvalid($title, "Title is required and must not exceed 200 characters.");
    }

    if (!publisher) {
      setFieldInvalid($publisher, "Publisher is required and must not exceed 100 characters.");
    } else if (publisher.length > 100) {
      setFieldInvalid($publisher, "Publisher is required and must not exceed 100 characters.");
    }

    if (!publish) {
      setFieldInvalid($publishDate, "Publish date is required.");
    }

    if (!deadline) {
      setFieldInvalid($deadlineDate, "Deadline date is required and must be on or after the publish date.");
    } else if (publish && deadline < publish) {
      setFieldInvalid($deadlineDate, "Deadline date is required and must be on or after the publish date.");
    }

    if (!content) {
      setFieldInvalid($content, "Content is required.");
    }

    var $first = firstInvalidField();
    if ($first.length) {
      $first.trigger("focus");
      return false;
    }
    return true;
  }

  function formPayload() {
    return {
      title: $.trim($title.val()),
      publisher: $.trim($publisher.val()),
      publishDate: $publishDate.val(),
      deadlineDate: $deadlineDate.val(),
      content: $.trim($content.val())
    };
  }

  function paginationPages(page, totalPages) {
    var pages = [];
    if (totalPages <= PAGINATION_WINDOW) {
      for (var i = 0; i < totalPages; i += 1) {
        pages.push(i);
      }
      return pages;
    }

    var half = Math.floor(PAGINATION_WINDOW / 2);
    var start = Math.max(0, page - half);
    var end = Math.min(totalPages - 1, start + PAGINATION_WINDOW - 1);
    start = Math.max(0, end - PAGINATION_WINDOW + 1);
    for (var p = start; p <= end; p += 1) {
      pages.push(p);
    }
    return pages;
  }

  function pageButton(label, targetPage, options) {
    options = options || {};
    var $item = $("<li>").addClass("page-item");
    var $button = $("<button>")
      .attr({
        type: "button",
        class: "page-link js-page"
      })
      .text(label)
      .data("page", targetPage);

    if (options.ariaLabel) {
      $button.attr("aria-label", options.ariaLabel);
    }
    if (options.current) {
      $item.addClass("active");
      $button.attr("aria-current", "page");
    }
    if (options.disabled) {
      $item.addClass("disabled");
      $button.prop("disabled", true).attr("aria-disabled", "true");
    }
    $item.append($button);
    return $item;
  }

  function renderPagination(page, totalPages, totalItems) {
    $pagination.empty();
    if (!totalPages || totalPages <= 1 || !totalItems) {
      $pagination.addClass("d-none");
      return;
    }

    $pagination.removeClass("d-none");
    var $list = $("<ul>").addClass("pagination flex-wrap mb-0");
    $list.append(pageButton("Previous", Math.max(0, page - 1), {
      ariaLabel: "Previous page",
      disabled: page <= 0
    }));

    var pages = paginationPages(page, totalPages);
    for (var i = 0; i < pages.length; i += 1) {
      var pageIndex = pages[i];
      var pageLabel = String(pageIndex + 1);
      $list.append(pageButton(pageLabel, pageIndex, {
        ariaLabel: "Page " + pageLabel,
        current: pageIndex === page
      }));
    }

    $list.append(pageButton("Next", Math.min(totalPages - 1, page + 1), {
      ariaLabel: "Next page",
      disabled: page >= totalPages - 1
    }));
    $pagination.append($list);
  }

  function renderItems(items) {
    $tbody.empty();
    $.each(items, function (_, item) {
      var title = asText(item.title);
      var $edit = $("<button>")
        .attr({
          type: "button",
          class: "btn btn-sm btn-outline-primary js-edit-announcement"
        })
        .text("Edit")
        .attr("aria-label", "Edit announcement " + title)
        .data("id", item.id);

      var $del = $("<button>")
        .attr({
          type: "button",
          class: "btn btn-sm btn-outline-danger js-delete-announcement"
        })
        .text("Delete")
        .attr("aria-label", "Delete announcement " + title)
        .data("id", item.id)
        .data("title", title);

      var $row = $("<tr>");
      $row.append($("<td>").text(title));
      $row.append($("<td>").text(asText(item.publishDate)));
      $row.append($("<td>").text(asText(item.deadlineDate)));
      $row.append($("<td>").append($edit));
      $row.append($("<td>").append($del));
      $tbody.append($row);
    });
  }

  function loadList(page) {
    if (listRequest && listRequest.readyState !== 4) {
      listRequest.abort();
    }

    var requestedPage = page;
    var token = (listRequestToken += 1);

    $empty.addClass("d-none");
    $table.closest(".table-responsive").removeClass("d-none");
    if ($feedback.hasClass("alert-danger")) {
      hideFeedback();
    }
    setListBusy(true);

    listRequest = $.ajax({
      url: ANNOUNCEMENTS_API,
      method: "GET",
      data: {
        page: requestedPage,
        size: PAGE_SIZE
      },
      dataType: "json"
    }).done(function (data) {
      if (token !== listRequestToken) {
        return;
      }
      if (!data || !$.isArray(data.items) || typeof data.page !== "number"
          || typeof data.totalPages !== "number" || typeof data.totalItems !== "number") {
        showFeedback("The announcement list could not be loaded.", true);
        $tbody.empty();
        $pagination.addClass("d-none").empty();
        currentItemCount = 0;
        return;
      }

      if (data.items.length === 0 && data.totalPages > 0 && data.page >= data.totalPages) {
        loadList(Math.max(0, data.totalPages - 1));
        return;
      }

      currentPage = data.page;
      currentItemCount = data.items.length;
      renderItems(data.items);
      renderPagination(data.page, data.totalPages, data.totalItems);

      if (data.items.length === 0) {
        $empty.removeClass("d-none");
        $table.closest(".table-responsive").addClass("d-none");
      } else {
        $empty.addClass("d-none");
        $table.closest(".table-responsive").removeClass("d-none");
      }
    }).fail(function (jqXHR, textStatus) {
      if (textStatus === "abort" || token !== listRequestToken) {
        return;
      }
      $tbody.empty();
      $pagination.addClass("d-none").empty();
      currentItemCount = 0;
      var error = parseError(jqXHR);
      showFeedback(error.message, true);
    }).always(function (unused, textStatus) {
      if (textStatus === "abort" || token !== listRequestToken) {
        return;
      }
      setListBusy(false);
    });
  }

  function populateForm(record) {
    $announcementId.val(asText(record.id));
    $title.val(asText(record.title));
    $publisher.val(asText(record.publisher));
    $publishDate.val(asText(record.publishDate));
    $deadlineDate.val(asText(record.deadlineDate));
    $content.val(asText(record.content));
    syncDeadlineMin();
    $modalTitle.text("Edit announcement");
    clearFieldErrors();
    hideFormError();
  }

  function hideModal($modal) {
    var el = $modal[0];
    var instance = getModal(el);
    if (el.classList.contains("showing")) {
      $modal.one("shown.bs.modal", function () {
        instance.hide();
      });
      return;
    }
    instance.hide();
  }

  function closeAnnouncementModal() {
    hideModal($announcementModal);
  }

  function openEdit(id, $trigger) {
    if (editRequest && editRequest.readyState !== 4) {
      editRequest.abort();
    }
    var token = (editRequestToken += 1);
    lastListTrigger = $trigger && $trigger.length ? $trigger[0] : null;
    $trigger.prop("disabled", true);

    editRequest = $.ajax({
      url: announcementUrl(id),
      method: "GET",
      dataType: "json"
    }).done(function (record) {
      if (token !== editRequestToken) {
        return;
      }
      resetAnnouncementForm();
      populateForm(record);
      $saveButton.prop("disabled", true);
      getModal($announcementModal[0]).show();
    }).fail(function (jqXHR, textStatus) {
      if (textStatus === "abort" || token !== editRequestToken) {
        return;
      }
      closeAnnouncementModal();
      resetAnnouncementForm();
      var error = parseError(jqXHR);
      showFeedback(error.message, true);
      loadList(currentPage);
    }).always(function () {
      $trigger.prop("disabled", false);
    });
  }

  function saveAnnouncement() {
    if (!validateForm()) {
      return;
    }
    if (saveRequest && saveRequest.readyState !== 4) {
      return;
    }

    var id = $announcementId.val();
    var isEdit = !!id;
    var payload = formPayload();

    setSaving(true);
    hideFormError();

    saveRequest = $.ajax({
      url: isEdit ? announcementUrl(id) : ANNOUNCEMENTS_API,
      method: isEdit ? "PUT" : "POST",
      contentType: "application/json",
      data: JSON.stringify(payload),
      dataType: "json"
    }).done(function () {
      closeAnnouncementModal();
      showFeedback(isEdit ? "Announcement updated." : "Announcement created.", false);
      loadList(currentPage);
    }).fail(function (jqXHR, textStatus) {
      if (textStatus === "abort") {
        return;
      }
      var error = parseError(jqXHR);
      if (jqXHR.status === 404) {
        closeAnnouncementModal();
        resetAnnouncementForm();
        showFeedback(error.message, true);
        loadList(currentPage);
        return;
      }
      var mapped = applyFieldErrors(error.fieldErrors);
      if (!mapped) {
        showFormError(error.message);
        showFeedback(error.message, true);
      } else {
        var unmapped = [];
        $.each(error.fieldErrors, function (field) {
          if (!FIELD_SELECTORS[field]) {
            unmapped.push(field);
          }
        });
        if (unmapped.length) {
          showFormError(error.message);
        }
      }
    }).always(function () {
      setSaving(false);
    });
  }

  function openDelete(id, title, $trigger) {
    pendingDeleteId = id;
    lastListTrigger = $trigger && $trigger.length ? $trigger[0] : null;
    $confirmDelete.prop("disabled", false);
    $deleteBody.text('Permanently delete "' + asText(title) + '"? This cannot be undone.');
    getModal($deleteModal[0]).show();
  }

  function confirmDelete() {
    if (pendingDeleteId == null) {
      return;
    }
    if (deleteRequest && deleteRequest.readyState !== 4) {
      return;
    }

    $confirmDelete.prop("disabled", true);

    deleteRequest = $.ajax({
      url: announcementUrl(pendingDeleteId),
      method: "DELETE"
    }).done(function () {
      hideModal($deleteModal);
      showFeedback("Announcement deleted.", false);
      var pageToLoad = currentPage;
      if (currentItemCount <= 1 && currentPage > 0) {
        pageToLoad = currentPage - 1;
      }
      loadList(pageToLoad);
    }).fail(function (jqXHR, textStatus) {
      if (textStatus === "abort") {
        return;
      }
      hideModal($deleteModal);
      var error = parseError(jqXHR);
      showFeedback(error.message, true);
      loadList(currentPage);
    }).always(function () {
      $confirmDelete.prop("disabled", false);
      pendingDeleteId = null;
    });
  }

  $(function () {
    $form.find(".invalid-feedback").each(function () {
      $(this).data("defaultMessage", $(this).text());
    });

    $("#new-announcement-button").on("click", function () {
      editRequestToken += 1;
      if (editRequest && editRequest.readyState !== 4) {
        editRequest.abort();
      }
      lastListTrigger = this;
      resetAnnouncementForm();
      $saveButton.prop("disabled", true);
      getModal($announcementModal[0]).show();
    });

    $announcementModal.on("show.bs.modal", function () {
      $saveButton.prop("disabled", true);
    });

    $announcementModal.on("shown.bs.modal", function () {
      $saveButton.prop("disabled", false);
      $title.trigger("focus");
    });

    $announcementModal.on("hidden.bs.modal", function () {
      resetAnnouncementForm();
      if (lastListTrigger && document.body.contains(lastListTrigger)) {
        lastListTrigger.focus();
      }
    });

    $deleteModal.on("hidden.bs.modal", function () {
      pendingDeleteId = null;
      $confirmDelete.prop("disabled", false);
      $deleteBody.text("This announcement will be permanently deleted.");
      if (lastListTrigger && document.body.contains(lastListTrigger)) {
        lastListTrigger.focus();
      }
    });

    $publishDate.on("change input", syncDeadlineMin);

    $form.on("submit", function (event) {
      event.preventDefault();
      saveAnnouncement();
    });

    $tbody.on("click", ".js-edit-announcement", function () {
      var $button = $(this);
      var id = $button.data("id");
      if (id == null) {
        return;
      }
      openEdit(id, $button);
    });

    $tbody.on("click", ".js-delete-announcement", function () {
      var $button = $(this);
      var id = $button.data("id");
      if (id == null) {
        return;
      }
      openDelete(id, $button.data("title"), $button);
    });

    $pagination.on("click", ".js-page", function (event) {
      event.preventDefault();
      var $button = $(this);
      if ($button.prop("disabled") || $button.closest(".page-item").hasClass("disabled")) {
        return;
      }
      var page = $button.data("page");
      if (typeof page !== "number") {
        return;
      }
      loadList(page);
    });

    $confirmDelete.on("click", confirmDelete);

    loadList(0);
  });
})(jQuery);
