(function () {
  function highlightSource(target) {
    if (!target || !target.classList || !target.classList.contains("source-card")) {
      return;
    }
    target.classList.remove("source-card-flash");
    void target.offsetWidth;
    target.classList.add("source-card-flash");
    target.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }

  document.addEventListener("click", function (event) {
    var anchor = event.target.closest('a.citation-chip[href^="#source-"]');
    if (!anchor) {
      return;
    }
    var href = anchor.getAttribute("href");
    if (!href || href.length < 2) {
      return;
    }
    var id = href.slice(1);
    var el = document.getElementById(id);
    if (el) {
      event.preventDefault();
      highlightSource(el);
    }
  });

  window.addEventListener("hashchange", function () {
    var hash = window.location.hash;
    if (!hash || hash.length < 2 || !hash.startsWith("#source-")) {
      return;
    }
    var el = document.getElementById(hash.slice(1));
    highlightSource(el);
  });

  if (window.location.hash && window.location.hash.startsWith("#source-")) {
    var initial = document.getElementById(window.location.hash.slice(1));
    highlightSource(initial);
  }
})();
