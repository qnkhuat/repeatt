(ns repeatt.core
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [compojure.core :as compojure]
   [compojure.route :as compojure-route]
   [hiccup.page :as h.page]
   [hiccup2.core :as h]
   [ring.adapter.jetty :as jetty]
   [ring.util.response :as response]))

(def ^:private audio-root "/Users/earther/fun/repeatt/scripts/pUo8e1x3L6E/split_audio/")
(assert (str/ends-with? audio-root "/"))
(def ^:private audio-ext "**{.wav,.webm}")
(def ^:private audio-files (fs/glob audio-root audio-ext))
(def ^:private rel-path->audio-file (into {} (zipmap (map #(str/replace (str %) audio-root "") audio-files)
                                                     audio-files)))

(defn- path->content
  ;; content of an audio file is its filename
  [path]
  (-> path
      (str/split #"/")
      last
      ;; remove extension
      (str/split #"\.")
      drop-last
      (->> (str/join "."))))

(defn- multi-html-response?
  "Is x of this form
  [[:p 1]
   [:p 2]]"
  [x]
  (and (coll? x)
       (pos? (count x))
       (coll? (first x))))

(defn hiccup->html-response
  "Render a hiccup html response."
  ([form]
   (hiccup->html-response form nil))
  ([resp doctype]
   (-> (if (multi-html-response? resp)
         (str/join (map #(h/html {} doctype %) resp))
         (str (h/html {} doctype resp)))
       response/response
       (response/content-type "text/html"))))

(defn with-nav-bar
  [children]
  [:div
   [:nav {:class "navbar navbar-expand-lg bg-dark"}
    [:div {:class "container-fluid"}
     [:a {:class "navbar-brand text-light"
          :href  "/"}
      "Repeatt"]]]
   children])

(def ^:private bootstrap-css
  [:link {:crossorigin "anonymous"
          :rel         "stylesheet"
          :integrity   "sha384-T3c6CoIi6uLrA9TneNEoa7RxnatzjcDSCmG1MXxSR1GAsXEV/Dwwykc2MPK8M2HN"
          :href        "https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css"}])

(def ^:private bootstrap-icon-css
  [:link {:rel  "stylesheet"
          :href "https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css"}])

(def ^:private htmx-js
  [:script {:src "https://unpkg.com/htmx.org@1.9.10"}])

(def ^:private bootstrap-js
  [:script {:src         "https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"
            :integrity   "sha384-C6RzsynM9kWDrMNeT87bh95OGNyZPhcTNXj1NW7RuBCsyN/o0jlpcV8Qyq46cDfL"
            :crossorigin "anonymous"}])

(defn ^:private bare-html
  [children & {:keys [scripts? navbar?]
               :or   {scripts?   true
                      navbar?    true}
               :as _options}]
  [:html {:lang "en"}
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
    (when scripts?
      bootstrap-css)
    (when scripts? bootstrap-icon-css)
    (when scripts? htmx-js)
    [:script {:src "/static/app.js"}]]
   [:body {:hx-boosted "true"}
    (cond-> children
      navbar?
      with-nav-bar)
    (when scripts?
      bootstrap-js)]])

(defn html-response
  "Given a children, render it as a whole page.

  By defaul the rendered page will have htmx, bootstrap and a navbar."
  [children & options]
  (hiccup->html-response (apply bare-html children options) (h.page/doctype :html5)))

(defn index
  [_req]
  (let [[first-rel-path] (first rel-path->audio-file)]
   (html-response
    [:div.container-fluid.d-flex.justify-content-center
     [:div#audio-wrapper
      [:audio#audio-player
       {:controls true}
       [:source {:src (format "/audio/%s" first-rel-path)}]]
      [:p#audio-content (path->content first-rel-path)]]])))

(compojure/defroutes app
  (compojure/GET "/" [] index)
  (compojure/GET "/audio/:file-name" [file-name] (response/file-response (str (get rel-path->audio-file file-name))))
  (compojure-route/not-found "Page not found"))

(defn -main
  [& _args]
  (jetty/run-jetty #'app {:port  3000
                          :join? false}))
