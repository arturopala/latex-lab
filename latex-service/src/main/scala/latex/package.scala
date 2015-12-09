package object latex {

  implicit class Forward[A](a: A) {
    def >>[B](f: A => B): B = f(a)
  }

}