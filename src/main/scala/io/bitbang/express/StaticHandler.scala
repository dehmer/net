package io.bitbang.express

import io.bitbang.http.{Response, Request}

/**
 * @author Horst Dehmer
 */
class StaticHandler(dir: String) extends ((Request, Response) => Any) {

  override def apply(req: Request, res: Response): Any = {
    println("static path: " + req.path)
    res.send("resource not found: " + req.path)
  }
}
