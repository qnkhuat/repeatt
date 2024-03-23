(ns repeatt.core
  (:gen-class)
  (:require
   [babashka.fs :as fs]
   [clojure.string :as str]
   [compojure.core :as compojure]
   [compojure.route :as compojure-route]
   [hiccup.page :as h.page]
   [hiccup2.core :as h]
   [ring.adapter.jetty :as jetty]
   [ring.util.response :as response]))

(def ^:private jetty-port (parse-long (or (System/getenv "REPEATT_JETTY_PORT") "3000")))
(def ^:private audio-root (or (System/getenv "REPEATT_AUDIO_ROOT") "./"))
(assert (str/ends-with? audio-root "/"))
(def ^:private audio-ext "**{.wav,.webm}")
(def ^:private audio-files (fs/glob audio-root audio-ext))
(def ^:private rel-path->abs-path (into {} (zipmap (map #(str/replace (str %) audio-root "") audio-files)
                                                   (map str audio-files))))

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
   [:div {:class "container-fluid bg-dark"
          :style {:height "50px"}}
    [:a {:class "navbar-brand text-light fs-4 text-center d-block"
         :style {:line-height "50px"}
         :href  "https://github.com/qnkhuat/repeatt"}
     "Repeatt"]]
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
    [:link {:rel "stylesheet"
            :href "/static/app.css"}]
    (when scripts?
      bootstrap-css)
    #_(when scripts? bootstrap-icon-css)
    #_(when scripts? htmx-js)]
   [:body {:hx-boosted "true"}
    (cond-> children
      navbar?
      with-nav-bar)
    (when scripts?
      bootstrap-js)
    [:script {:src "/static/app.js"}]]])

(defn html-response
  "Given a children, render it as a whole page."
  [children & options]
  (hiccup->html-response (apply bare-html children options) (h.page/doctype :html5)))

(defn index
  [_req]
  (let [[first-rel-path] (first rel-path->abs-path)]
    (html-response
     [:div {:class "container-fluid d-flex justify-content-center"}
      [:div {:class "body-width"}
       [:div#audio-recorder {:class "mt-3"}
        [:div {:class "w-100 d-flex justify-content-center mb-3"}
         [:button#btn-do-record {:class   "btn btn-danger me-3"
                                 :onclick "startRecording()"}
          "Record"]
         [:button#btn-stop-record {:class "btn btn-danger me-3"
                                   :onclick "stopRecording()"}
          "Stop"]]
        [:audio#audio-recording {:class    "w-100 d-block"
                                 :controls true}]]
       [:div#audio-wrapper {:class "mt-3"}
        [:p#audio-content {:class "text-center mt-2"}
                 (path->content first-rel-path)]
        [:audio#audio-player
         {:controls true
          :class    "w-100"}
         [:source#audio-source {:src (format "/audio/%s" first-rel-path)}]]]
       [:div#audio-browser
        {:class "mt-5"
         :style "height: 800px; overflow: scroll"}
        [:ul {:class "p-0"}
         (for [rel-path (keys rel-path->abs-path)
               :let [content (path->content rel-path)]]
           [:li {:class "d-flex py-2" :style "height: 50px; align-items: center"}
            [:button {:class "btn btn-primary me-2"
                      :onclick (format "document.getElementById('audio-source').src = \"%s\";
                                       document.getElementById('audio-content').innerHTML = \"%s\";
                                       document.getElementById('audio-player').load();"
                                       (format "/audio/%s" rel-path)
                                       content)}
             "play"] [:p {:class "m-0"} content]])]]]])))

(compojure/defroutes app
  (compojure/GET "/" [] index)
  (compojure/GET "/audio/:file" [file] (response/file-response (str (get rel-path->abs-path file))))
  (compojure/GET "/static/:file" [file] (response/resource-response (format "static/%s" file)))
  (compojure-route/not-found "Page not found"))

(defn -main
  [& _args]
  (jetty/run-jetty #'app {:port  jetty-port
                          :join? false})
  (println "Running at port:" jetty-port))
