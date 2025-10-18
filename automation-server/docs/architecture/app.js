(function () {
  function ready(fn) {
    if (document.readyState !== "loading") {
      fn();
    } else {
      document.addEventListener("DOMContentLoaded", fn);
    }
  }

  ready(function () {
    var cards = document.querySelectorAll(".module-card");
    var detailBlocks = document.querySelectorAll(".module-detail");
    var buildTime = document.getElementById("build-time");

    function hideAll() {
      detailBlocks.forEach(function (block) {
        block.classList.remove("active");
      });
    }

    function showTarget(targetId) {
      hideAll();
      var target = document.getElementById(targetId);
      if (target) {
        target.classList.add("active");
        target.scrollIntoView({ behavior: "smooth", block: "center" });
      }
    }

    cards.forEach(function (card) {
      var button = card.querySelector("button");
      if (!button) {
        return;
      }
      button.addEventListener("click", function () {
        var targetId = card.getAttribute("data-target");
        if (!targetId) {
          return;
        }
        var target = document.getElementById(targetId);
        if (target && target.classList.contains("active")) {
          target.classList.remove("active");
        } else {
          showTarget(targetId);
        }
      });
    });

    if (buildTime) {
      var now = new Date();
      buildTime.textContent = now.toLocaleString("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
      });
    }
  });
})();
