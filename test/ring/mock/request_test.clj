(ns ring.mock.request-test
  (:require [clojure.java.io :as io])
  (:use clojure.test
        ring.mock.request))

(deftest test-request
  (testing "relative uri"
    (is (= (request :get "/foo")
           {:protocol "HTTP/1.1"
            :server-port 80
            :server-name "localhost"
            :remote-addr "127.0.0.1"
            :uri "/foo"
            :scheme :http
            :request-method :get
            :headers {"host" "localhost"}})))
  (testing "absolute uri"
    (let [request (request :post "https://example.com:8443/foo?bar=baz" {"quux" "zot"})
          literal-request (dissoc request :body)
          body (:body request)]
      (is (= literal-request
             {:protocol "HTTP/1.1"
              :server-port 8443
              :server-name "example.com"
              :remote-addr "127.0.0.1"
              :uri "/foo"
              :query-string "bar=baz"
              :scheme :https
              :request-method :post
              :content-type "application/x-www-form-urlencoded"
              :content-length 8
              :headers {"host" "example.com:8443"
                        "content-type" "application/x-www-form-urlencoded"
                        "content-length" "8"}}))
      (is (= (slurp body) "quux=zot"))))
  (testing "absolute http uri with implicit port"
    (let [{:keys [headers server-port]} (request :get "http://example.test")]
      (is (= server-port 80))
      (is (= (get headers "host") "example.test"))))
  (testing "absolute https uri with implicit port"
    (let [{:keys [headers server-port]} (request :get "https://example.test")]
      (is (= server-port 443))
      (is (= (get headers "host") "example.test"))))
  (testing "nil path"
    (is (= (:uri (request :get "http://example.com")) "/")))
  (testing "only params in :get"
    (is (= (:query-string (request :get "/?a=b"))
           "a=b")))
  (testing "added params in :get"
    (is (= (:query-string (request :get "/" (array-map :x "y" :z "n")))
           "x=y&z=n"))
    (is (= (:query-string (request :get "/?a=b" {:x "y"}))
           "a=b&x=y"))
    (is (= (:query-string (request :get "/?" {:x "y"}))
           "x=y"))
    (is (= (:query-string (request :get "/" {:x "a b"}))
           "x=a+b")))
  (testing "added params in :delete"
    (is (= (:query-string (request :delete "/" (array-map :x "y" :z "n")))
           "x=y&z=n")))
  (testing "added params in :post"
    (let [req (request :post "/" (array-map :x "y" :z "n"))]
      (is (= (slurp (:body req))
             "x=y&z=n"))
      (is (not (contains? req :query-string))))
    (let [req (request :post "/?a=b" {:x "y"})]
      (is (= (slurp (:body req))
             "x=y"))
      (is (= (:query-string req)
             "a=b")))
    (let [req (request :post "/?" {:x "y"})]
      (is (= (slurp (:body req))
             "x=y"))
      (is (= (:query-string req)
             "")))
    (let [req (request :post "/" {:x "a b"})]
      (is (= (slurp (:body req))
             "x=a+b"))
      (is (nil? (:query-string req))))
    (let [req (request :post "/?a=b")]
      (is (nil? (:body req)))
      (is (= (:query-string req)
             "a=b"))))
  (testing "added params in :put"
    (let [req (request :put "/" (array-map :x "y" :z "n"))]
      (is (= (slurp (:body req)) "x=y&z=n")))))

(deftest test-header
  (is (= (header {} "X-Foo" "Bar")
         {:headers {"x-foo" "Bar"}}))
  (is (= (header {} :x-foo "Bar")
         {:headers {"x-foo" "Bar"}})))

(deftest test-cookie
  (is (= (cookie {} "Foo" "Bar")
         {:headers {"cookie" "Foo=Bar"}}))
  (is (= (cookie {:headers {"cookie" "a=b"}} "c" "d")
         {:headers {"cookie" "a=b; c=d"}}))
  (is (= (cookie {} :foo "bar")
         {:headers {"cookie" "foo=bar"}})))

(deftest test-content-type
  (is (= (content-type {} "text/html")
         {:content-type "text/html"
          :headers {"content-type" "text/html"}})))

(deftest test-content-length
  (is (= (content-length {} 10)
         {:content-length 10
          :headers {"content-length" "10"}})))

(deftest test-query-string
  (testing "nil"
    (is (= (query-string {} nil)
           {})))
  (testing "string"
    (is (= (query-string {} "a=b")
           {:query-string "a=b"})))
  (testing "map of params"
    (is (= (query-string {} {:a "b"})
           {:query-string "a=b"})))
  (testing "overwriting"
    (is (= (-> {}
               (query-string {:a "b"})
               (query-string {:c "d"}))
           {:query-string "c=d"}))))

(deftest test-body
  (testing "string body"
    (let [resp (body {} "Hello World")]
      (is (instance? java.io.InputStream (:body resp)))
      (is (= (slurp (:body resp)) "Hello World"))
      (is (= (:content-length resp) 11))))
  (testing "map body"
    (let [resp (body {} (array-map :foo "bar" :fi ["fi" "fo" "fum"]))]
      (is (instance? java.io.InputStream (:body resp)))
      (is (= (slurp (:body resp)) "foo=bar&fi=fi&fi=fo&fi=fum"))
      (is (= (:content-length resp) 26))
      (is (= (:content-type resp)
             "application/x-www-form-urlencoded"))))
  (testing "bytes body"
    (let [resp (body {} (.getBytes "foo"))]
      (is (instance? java.io.InputStream (:body resp)))
      (is (= (slurp (:body resp)) "foo"))
      (is (= (:content-length resp) 3)))))

(deftest test-json-body
  (testing "json body"
    (let [resp (json-body {} {:baz ["qu" "qi" "qo"]})]
      (is (= (:content-type resp) "application/json"))
      (is (instance? java.io.InputStream (:body resp)))
      (is (= (slurp (:body resp))
             "{\"baz\":[\"qu\",\"qi\",\"qo\"]}"))
      (is (= (:content-length resp) 24)))))

(defmacro when-clojure-spec
  [& body]
  (when (try
          (require '[clojure.spec :as s])
          true
          (catch Exception _
            (binding [*out* *err*]
              (println "ring-mock: Skipping ring-spec tests."))
            false))
    `(do
       (require 'ring.core.spec)
       ~@body)))

(deftest test-specs
  (when-clojure-spec
   (doseq [req [(-> (request :get "/foo/bar") (query-string nil))
                (request :put "/foo/bar")
                (request :something "/foo" {:params true})]]
     (is (s/valid? :ring/request req)
         (s/explain-str :ring/request req)))))
